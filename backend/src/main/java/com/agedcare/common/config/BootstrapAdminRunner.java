package com.agedcare.common.config;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.agedcare.modules.user.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
@Component
@Order(-100)
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {
    private final SysUserMapper users;
    private final PasswordEncoder encoder;
    private final DispatchClusterLock lock;
    @Value("${BOOTSTRAP_ADMIN_USERNAME:admin}") private String username;
    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") private String password;
    public void run(ApplicationArguments ignored) {
        boolean completed=lock.run("bootstrap",30,()-> {
            if(users.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole,"ADMIN"))>0) return;
            if(password==null || password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
                throw new IllegalStateException("空数据库首次启动需设置12至72字节的BOOTSTRAP_ADMIN_PASSWORD");
            if(username==null || !username.matches("[a-zA-Z0-9_-]{3,32}")) throw new IllegalStateException("初始管理员账号需为3至32位字母、数字、下划线或连字符");
            users.insert(SysUser.builder().username(username).password(encoder.encode(password)).role("ADMIN")
                    .realName("社区管理员").status(1).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        });
        if(!completed) throw new IllegalStateException("其他实例正在初始化管理员，请稍后重启");
    }
}
