package com.agedcare.modules.auth.service;

import com.agedcare.common.api.ResultCode;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.JwtTokenProvider;
import com.agedcare.common.security.LoginUser;
import com.agedcare.modules.auth.dto.AuthResponse;
import com.agedcare.modules.auth.dto.LoginRequest;
import com.agedcare.modules.auth.dto.WechatLoginRequest;
import com.agedcare.modules.user.entity.SysUser;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import com.agedcare.common.security.OrderAccessPolicy;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @Value("${wechat.miniapp.appid:}")
    private String wechatAppId;

    @Value("${wechat.miniapp.secret:}")
    private String wechatSecret;

    private final OkHttpClient httpClient = new OkHttpClient();

    public AuthResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername())
                .or()
                .eq(SysUser::getPhone, req.getUsername()));

        if (user == null) {
            throw new BusinessException("账号或手机号不存在");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("该账号已被管理员停用，请联系社区工作人员");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误，请重新输入");
        }

        return createAuthResponse(user);
    }

    public AuthResponse mockLogin(String role, String username) {
        if (!devMode) {
            throw new BusinessException(403, "当前运行环境已禁用模拟演示切换登录");
        }

        if (!StringUtils.hasText(username) && !StringUtils.hasText(role)) {
            throw new BusinessException(400, "必须指定具体的演示用户名或角色");
        }

        String selected = StringUtils.hasText(username) ? username : switch (role.toUpperCase()) {
            case "RESIDENT" -> "resident01";
            case "STAFF" -> "staff01";
            default -> throw new BusinessException(403, "演示入口仅支持居民和工作人员");
        };
        if (!isDemoUsername(selected)) throw new BusinessException(403, "只能切换指定的演示账号");
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, selected);

        SysUser user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException("未找到对应的演示测试账号");
        }
        if (!Set.of("RESIDENT", "STAFF").contains(user.getRole()) || !Integer.valueOf(1).equals(user.getStatus()))
            throw new BusinessException(403, "演示账号已停用或身份不适用");
        return createAuthResponse(user);
    }

    public static boolean isDemoUsername(String value) {
        return value != null && (Set.of("resident01", "family01", "staff01").contains(value) || value.matches("demo_reg_[a-f0-9]{32}"));
    }

    public List<DemoAccount> demoAccounts() {
        if (!devMode) throw new BusinessException(403, "演示模式未开启");
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1).in(SysUser::getRole, "RESIDENT", "STAFF")
                .and(q -> q.in(SysUser::getUsername, "resident01", "family01", "staff01").or().likeRight(SysUser::getUsername, "demo_reg_"))
                .orderByDesc(SysUser::getCreatedAt).last("LIMIT 50")).stream()
                .filter(u -> isDemoUsername(u.getUsername())).map(u -> new DemoAccount(u.getUsername(), u.getRealName(), u.getRole())).toList();
    }
    public record DemoAccount(String username, String realName, String role) {}

    @Transactional(rollbackFor = Exception.class)
    public AuthResponse demoRegister(String requestId, String realName) {
        if (!devMode) throw new BusinessException(403, "演示模式未开启");
        if (requestId == null || !requestId.matches("[a-f0-9]{32}") || !StringUtils.hasText(realName) || realName.trim().length() > 32)
            throw new BusinessException("请填写演示姓名并重新提交");
        String username = "demo_reg_" + requestId;
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (existing != null) return mockLogin(null, username);
        SysUser user = SysUser.builder().username(username).realName(realName.trim())
                .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .role("RESIDENT").status(1).phone(null).openid(null).avatar("")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        try { userMapper.insert(user); }
        catch (org.springframework.dao.DuplicateKeyException e) {
            // Current read also sees a concurrent retry that committed after our initial snapshot.
            var retried = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("FOR UPDATE"));
            if (retried == null || !Set.of("RESIDENT", "STAFF").contains(retried.getRole()) || !Integer.valueOf(1).equals(retried.getStatus()))
                throw new BusinessException("演示账号状态已变化，请重新创建");
            return createAuthResponse(retried);
        }
        var response = createAuthResponse(user);
        response.setNewUser(true);
        return response;
    }

    public AuthResponse refresh() {
        SysUser user = userMapper.selectById(OrderAccessPolicy.user().getId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(ResultCode.UNAUTHORIZED);
        return createAuthResponse(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResponse wechatLogin(WechatLoginRequest req) {
        String openid = fetchWechatOpenid(req.getCode());
        if (!StringUtils.hasText(openid)) {
            throw new BusinessException("获取微信 OpenID 失败，请检查微信配置或 Code 有效性");
        }

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getOpenid, openid));
        boolean newUser = user == null;

        if (user != null) {
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException("该微信关联账号已被管理员停用，请联系社区处理");
            }
        } else {
            // 首次微信进入，自动注册为居民账号，生成不可逆随机口令，phone 设为 null 防止 MySQL 唯一键冲突
            String role = UserRole.RESIDENT.getCode();
            String generatedUsername = "wx_" + openid.substring(Math.max(0, openid.length() - 8));
            String nickName = StringUtils.hasText(req.getNickName()) ? req.getNickName() : "微信居民用户";

            user = SysUser.builder()
                    .username(generatedUsername)
                    .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                    .realName(nickName)
                    .phone(null)
                    .avatar(req.getAvatarUrl() != null ? req.getAvatarUrl() : "")
                    .role(role)
                    .openid(openid)
                    .status(1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }

        var response = createAuthResponse(user);
        response.setNewUser(newUser);
        return response;
    }

    private String fetchWechatOpenid(String code) {
        if (!StringUtils.hasText(wechatAppId) || !StringUtils.hasText(wechatSecret)) {
            if (devMode) {
                log.warn("开发演示模式且微信配置未就绪，使用模拟 openid 用于联调");
                return "mock_openid_" + code;
            }
            log.error("系统微信配置缺失，且未开启开发演示模式，拒绝登录");
            throw new BusinessException("系统微信登录未就绪，请使用账号密码登录或联系管理员");
        }

        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                wechatAppId, wechatSecret, code
        );

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                if (jsonNode.has("openid")) {
                    return jsonNode.get("openid").asText();
                } else {
                    log.error("微信返回错误: {}", responseBody);
                }
            }
        } catch (IOException e) {
            log.error("调用微信 jscode2session 接口异常", e);
        }
        return null;
    }

    private AuthResponse createAuthResponse(SysUser user) {
        LoginUser loginUser = LoginUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .role(user.getRole())
                .openid(user.getOpenid())
                .avatar(user.getAvatar())
                .build();

        String token = jwtTokenProvider.generateToken(loginUser);
        return AuthResponse.builder()
                .token(token)
                .userInfo(loginUser)
                .build();
    }
}
