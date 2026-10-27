package com.agedcare.security;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.*;
import com.agedcare.modules.auth.service.AuthService;
import com.agedcare.modules.user.entity.SysUser;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

class DemoRegistrationTest {
    final SysUserMapper users = mock(SysUserMapper.class);
    final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    final AuthService service = new AuthService(users, mock(PasswordEncoder.class), jwt, new ObjectMapper());
    final String key = "0123456789abcdef0123456789abcdef";
    @BeforeEach void setup() { ReflectionTestUtils.setField(service,"devMode",true); }
    @AfterEach void clear() { UserContext.clear(); }
    @Test void demoRegistrationCreatesResidentWithoutRealOpenidOrPhone() {
        var response = service.demoRegister(key," 演示新人 ");
        var capture = ArgumentCaptor.forClass(SysUser.class); verify(users).insert(capture.capture());
        assertEquals("RESIDENT",capture.getValue().getRole()); assertNull(capture.getValue().getOpenid()); assertNull(capture.getValue().getPhone());
        assertEquals("demo_reg_"+key,capture.getValue().getUsername()); assertEquals("演示新人",capture.getValue().getRealName()); assertTrue(response.isNewUser());
    }
    @Test void retryReturnsExistingAccountRatherThanInsertingTwice() {
        when(users.selectOne(any())).thenReturn(SysUser.builder().id(2L).username("demo_reg_"+key).role("RESIDENT").status(1).build());
        assertFalse(service.demoRegister(key,"演示新人").isNewUser()); verify(users,never()).insert(any(SysUser.class));
    }
    @Test void demoModeCannotLogInAsAdminOrAnArbitraryRealUser() {
        assertThrows(BusinessException.class, () -> service.mockLogin("ADMIN",null));
        assertThrows(BusinessException.class, () -> service.mockLogin(null,"real_wechat_user"));
        when(users.selectOne(any())).thenReturn(SysUser.builder().role("ADMIN").status(1).build());
        assertThrows(BusinessException.class, () -> service.mockLogin(null,"staff01"));
    }
    @Test void disabledDemoUserAndClosedModeAreRejected() {
        when(users.selectOne(any())).thenReturn(SysUser.builder().role("STAFF").status(0).build());
        assertThrows(BusinessException.class, () -> service.mockLogin(null,"staff01"));
        ReflectionTestUtils.setField(service,"devMode",false);
        assertThrows(BusinessException.class, () -> service.demoRegister(key,"新人"));
    }
    @Test void refreshUsesCurrentDatabaseRoleInsteadOfOldTokenRole() {
        UserContext.setUser(LoginUser.builder().id(2L).role("RESIDENT").build());
        when(users.selectById(2L)).thenReturn(SysUser.builder().id(2L).role("STAFF").status(1).build());
        assertEquals("STAFF",service.refresh().getUserInfo().getRole());
    }
}
