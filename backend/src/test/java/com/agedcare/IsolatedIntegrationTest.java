package com.agedcare;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.MySQLContainer;
import java.sql.DriverManager;

/** 仅使用本测试创建的固定版本容器，不接受外部数据库地址。 */
public abstract class IsolatedIntegrationTest {
    private static String url;
    private static String username;
    private static String password;
    private static MySQLContainer<?> container;
    private static final java.nio.file.Path storage = testStorage();
    private static java.nio.file.Path testStorage() {
        try { return java.nio.file.Files.createTempDirectory("agedcare-integration-"); }
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static synchronized void initialize() {
        if (url != null) return;
        container = new MySQLContainer<>(org.testcontainers.utility.DockerImageName.parse("mysql@sha256:6ea90827b1100f8f2ae306a539f86d2c264a26ed435a2a9f75551dd5c3aeb242").asCompatibleSubstituteFor("mysql")).withDatabaseName("aged_care_db");
        container.start();
        url = container.getJdbcUrl(); username = container.getUsername(); password = container.getPassword();
        try (var connection = DriverManager.getConnection(url, username, password)) {
            if (!connection.getMetaData().getDatabaseProductVersion().startsWith("8.4.11"))
                throw new IllegalStateException("集成测试必须使用固定的MySQL 8.4.11");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
        } catch (Exception e) { throw new IllegalStateException("隔离测试数据库初始化失败", e); }
    }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        initialize();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("app.private-media-dir", () -> storage.resolve("private").toString());
        registry.add("app.upload-fallback-dir", () -> storage.resolve("fallback").toString());
        registry.add("app.service-images-dir", () -> storage.resolve("service-images").toString());
    }
}
