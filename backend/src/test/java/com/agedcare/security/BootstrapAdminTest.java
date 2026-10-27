package com.agedcare.security;
import com.agedcare.common.config.*;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.agedcare.modules.user.entity.SysUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
class BootstrapAdminTest {
    @Test void emptyDatabaseNeedsExplicitSecretAndNeverStoresPlaintext() {
        var users=mock(SysUserMapper.class);var encoder=mock(PasswordEncoder.class);var lock=mock(DispatchClusterLock.class);
        when(lock.run(anyString(),anyInt(),any())).thenAnswer(call->{((Runnable)call.getArgument(2)).run();return true;});
        when(users.selectCount(any())).thenReturn(0L);
        var runner=new BootstrapAdminRunner(users,encoder,lock);
        ReflectionTestUtils.setField(runner,"username","admin");ReflectionTestUtils.setField(runner,"password","");
        assertThrows(IllegalStateException.class,()->runner.run(null));verify(users,never()).insert(any(SysUser.class));
        ReflectionTestUtils.setField(runner,"password","generated-test-secret-with-enough-length");
        when(encoder.encode(anyString())).thenReturn("bcrypt-hash");
        runner.run(null);
        var user=ArgumentCaptor.forClass(SysUser.class);verify(users).insert(user.capture());
        assertEquals("bcrypt-hash",user.getValue().getPassword());
        when(users.selectCount(any())).thenReturn(1L);runner.run(null);verify(users,times(1)).insert(any(SysUser.class));
    }
}
