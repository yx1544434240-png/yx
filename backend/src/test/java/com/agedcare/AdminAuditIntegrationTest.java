package com.agedcare;
import com.agedcare.common.security.*;
import com.agedcare.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditIntegrationTest extends IsolatedIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @org.springframework.boot.test.mock.mockito.SpyBean JdbcTemplate jdbc;
    @Autowired SysUserMapper users;
    @org.junit.jupiter.api.AfterEach void reset(){org.mockito.Mockito.reset(jdbc);}
    @Test void failedAuditRollsBackBusinessMutation() throws Exception {
        String token=jwt.generateToken(LoginUser.builder().id(1L).role("ADMIN").username("admin").build());
        Integer before=users.selectById(5L).getStatus();
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated audit storage failure"))
                .when(jdbc).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO operation_log"),org.mockito.ArgumentMatchers.any(Object[].class));
        mvc.perform(post("/users/5/status").param("status",before==1?"0":"1").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.code").value(500));
        assertEquals(before,users.selectById(5L).getStatus());
    }
    @Test void successfulAdminMutationWritesAuditAndInvalidOrForbiddenMutationDoesNot() throws Exception {
        String token=jwt.generateToken(LoginUser.builder().id(1L).role("ADMIN").username("admin").build());
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class);
        mvc.perform(post("/users/5/status").param("status","1").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.code").value(200));
        assertEquals(before+1,jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class));
        var details=jdbc.queryForObject("SELECT details FROM operation_log ORDER BY created_at DESC,id DESC LIMIT 1",String.class);
        assertTrue(details.contains("/users/5/status")); assertFalse(details.contains(token));
        // 即使令牌声称ADMIN，数据库中的居民角色也必须使其降权。
        String forgedRole=jwt.generateToken(LoginUser.builder().id(5L).role("ADMIN").build());
        mvc.perform(get("/operation-logs/page").header("Authorization","Bearer "+forgedRole)).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/media/private/fulfillment/1/2/01234567-89ab-cdef-0123-456789abcdef.jpg")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/media/fallback/fulfillment/20261021/0123456789abcdef0123456789abcdef.jpg")).andExpect(jsonPath("$.code").value(401));
        assertEquals(before+1,jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class));
    }
}
