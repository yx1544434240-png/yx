package com.agedcare;

import com.agedcare.common.config.JacksonConfig;
import com.agedcare.common.exception.GlobalExceptionHandler;
import com.agedcare.common.security.*;
import com.agedcare.modules.auth.controller.*;
import com.agedcare.modules.auth.service.AuthService;
import com.agedcare.modules.staff.controller.StaffApplicationController;
import com.agedcare.modules.staff.service.StaffApplicationService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Explicit opt-in local MySQL test. Creates and drops only its own random test database; no Docker. */
@EnabledIfEnvironmentVariable(named="AGEDCARE_LOCAL_INTEGRATION", matches="true")
@SpringBootTest(classes=EnrollmentLocalIntegrationTest.Config.class, properties={
        "spring.profiles.active=dev", "app.dev-mode=true", "jwt.secret=local-integration-key-at-least-32-characters", "app.lan-sync.enabled=false" })
@AutoConfigureMockMvc
class EnrollmentLocalIntegrationTest {
    static final String DATABASE = "agedcare_enrollment_test_" + UUID.randomUUID().toString().replace("-", "");
    static final String ROOT_URL = "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    static final String USER = System.getenv().getOrDefault("AGEDCARE_TEST_DB_USER", "root");
    static final String PASSWORD = System.getenv("AGEDCARE_TEST_DB_PASSWORD");
    static boolean created;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        if (!DATABASE.matches("agedcare_enrollment_test_[a-f0-9]{32}") || PASSWORD == null) throw new IllegalStateException("Local test requires explicit test credentials");
        try (var connection = DriverManager.getConnection(ROOT_URL, USER, PASSWORD); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `"+DATABASE+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); created = true;
        }
        String url = ROOT_URL.replace("3306/?", "3306/"+DATABASE+"?");
        String schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?m)^CREATE DATABASE.*;\\R?", "").replaceAll("(?m)^USE .*;\\R?", "");
        if (schema.contains("USE `aged_care_db`") || schema.contains("CREATE DATABASE")) throw new IllegalStateException("Unsafe test schema");
        try (var connection = DriverManager.getConnection(url, USER, PASSWORD)) {
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }
    @AfterAll static void cleanup() throws Exception {
        if (created && DATABASE.matches("agedcare_enrollment_test_[a-f0-9]{32}"))
            try (var connection = DriverManager.getConnection(ROOT_URL, USER, PASSWORD); var statement = connection.createStatement()) { statement.execute("DROP DATABASE `"+DATABASE+"`"); }
    }
    @Configuration @EnableAutoConfiguration
    @MapperScan({"com.agedcare.modules.staff.mapper", "com.agedcare.modules.user.mapper"})
    @Import({AuthService.class, StaffApplicationService.class, JwtTokenProvider.class, AuthInterceptor.class, UploadRateLimiter.class,
            AuthController.class, MockAuthController.class, StaffApplicationController.class, GlobalExceptionHandler.class, JacksonConfig.class})
    static class Config {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
        @Bean WebMvcConfigurer authRoutes(AuthInterceptor auth) {
            return new WebMvcConfigurer() { public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(auth).addPathPatterns("/**"); } };
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider jwt;
    JsonNode call(String path, String token, Object body) throws Exception {
        var request = post(path).contentType("application/json").content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer "+token);
        return json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    JsonNode register(String token, String key) throws Exception { return call("/auth/demo-register",token,Map.of("requestId",key,"realName","答辩新用户")); }
    Map<String,Object> form(int revision, String phone) { return Map.of("revision",revision,"realName","入驻新人","phone",phone,"serviceArea","阳光花园社区","introduction","社区助老服务经历"); }
    Map<String,Object> audit(String id, int revision, boolean approve) {
        return Map.of("id",id,"revision",revision,"approve",approve,"reason",approve ? "核验通过" : "补充服务经历",
                "stationName","测试服务站","longitude",116.407396,"latitude",39.904199);
    }
    @Test void oneDeviceRegistrationRejectionApprovalAndRoleRefreshUseRealSqlTransactions() throws Exception {
        jdbc.update("INSERT INTO sys_user(id,username,password,real_name,role,status) VALUES(1,'local-admin','unused','管理员','ADMIN',1),(2,'local-resident','unused','居民','RESIDENT',1)");
        String admin = jwt.generateToken(LoginUser.builder().id(1L).role("ADMIN").build());
        String resident = jwt.generateToken(LoginUser.builder().id(2L).role("RESIDENT").build());
        String key = "0123456789abcdef0123456789abcdef";
        assertEquals(401, register(null,key).path("code").asInt());
        JsonNode registration = register(resident,key); assertEquals(200,registration.path("code").asInt());
        assertTrue(registration.path("data").path("newUser").asBoolean());
        String token = registration.at("/data/token").asText(), id = registration.at("/data/userInfo/id").asText();
        assertEquals(id,register(resident,key).at("/data/userInfo/id").asText());
        assertNull(jdbc.queryForObject("SELECT openid FROM sys_user WHERE id=?",String.class,id));
        assertEquals(200,call("/staff/applications/submit",token,form(0,"13812345678")).path("code").asInt());
        String applicationId = jdbc.queryForObject("SELECT id FROM staff_application WHERE user_id=?",String.class,id);
        assertEquals("RESIDENT",jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,id));
        assertEquals(403,call("/staff/applications/audit",token,audit(applicationId,1,true)).path("code").asInt());
        assertEquals(200,call("/staff/applications/audit",admin,audit(applicationId,1,false)).path("code").asInt());
        assertEquals(200,call("/staff/applications/submit",token,form(1,"13812345678")).path("code").asInt());
        assertNotEquals(200,call("/staff/applications/audit",admin,audit(applicationId,1,true)).path("code").asInt());
        assertEquals(200,call("/staff/applications/audit",admin,audit(applicationId,2,true)).path("code").asInt());
        assertEquals("STAFF",call("/auth/refresh",token,Map.of()).at("/data/userInfo/role").asText());
        assertEquals("RESTING",jdbc.queryForObject("SELECT current_status FROM staff_profile WHERE user_id=?",String.class,id));
        assertNotEquals(200,call("/staff/applications/audit",admin,audit(applicationId,2,true)).path("code").asInt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM staff_profile WHERE user_id=?",Integer.class,id));

        // A phone conflict must roll back both approval and role creation.
        var second = register(resident,"1123456789abcdef0123456789abcdef");
        String secondId = second.at("/data/userInfo/id").asText(), secondToken = second.at("/data/token").asText();
        assertEquals(200,call("/staff/applications/submit",secondToken,form(0,"13812345678")).path("code").asInt());
        String secondApplication = jdbc.queryForObject("SELECT id FROM staff_application WHERE user_id=?",String.class,secondId);
        assertNotEquals(200,call("/staff/applications/audit",admin,audit(secondApplication,1,true)).path("code").asInt());
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM staff_application WHERE user_id=?",String.class,secondId));
        assertEquals("RESIDENT",jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,secondId));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM staff_profile WHERE user_id=?",Integer.class,secondId));
    }
}
