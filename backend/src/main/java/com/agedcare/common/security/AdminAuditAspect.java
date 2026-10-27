package com.agedcare.common.security;

import com.agedcare.common.api.Result;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import java.util.Map;

/** 管理写操作与审计写入同一事务；日志不保存密码、令牌、手机号或完整请求体。 */
@Aspect
@Component
@RequiredArgsConstructor
public class AdminAuditAspect {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;

    @Around("execution(public * com.agedcare.modules..controller..*(..))")
    public Object audit(ProceedingJoinPoint call) throws Throwable {
        LoginUser user = UserContext.getUser();
        var attributes = RequestContextHolder.getRequestAttributes();
        if (user == null || !"ADMIN".equals(user.getRole()) || !(attributes instanceof ServletRequestAttributes servlet)
                || !java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(servlet.getRequest().getMethod())) return call.proceed();
        return transaction.execute(status -> {
            try {
                Object result = call.proceed();
                if (result instanceof Result<?> response && response.getCode() == 200) {
                    Long target = targetId(call.getArgs());
                    if (target == null && response.getData() != null) target = targetId(new Object[]{response.getData()});
                    String action = call.getSignature().getDeclaringType().getSimpleName() + "." + call.getSignature().getName();
                    var request = servlet.getRequest();
                    jdbc.update("INSERT INTO operation_log (id,operator_id,operator_name,action,target_type,target_id,details,ip,created_at) VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                            IdWorker.getId(), user.getId(), user.getRealName(), action.substring(0, Math.min(64, action.length())),
                            call.getSignature().getDeclaringType().getSimpleName(), target,
                            json.writeValueAsString(Map.of("method", request.getMethod(), "path", request.getRequestURI())), request.getRemoteAddr());
                }
                return result;
            } catch (RuntimeException | Error e) { throw e; }
            catch (Throwable e) { throw new IllegalStateException("管理操作或审计写入失败", e); }
        });
    }

    private Long targetId(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Long value) return value;
            if (argument == null) continue;
            for (String getter : new String[]{"getOrderId", "getBindingId", "getStaffId", "getId"}) {
                try {
                    Object value = argument.getClass().getMethod(getter).invoke(argument);
                    if (value instanceof Long id) return id;
                } catch (ReflectiveOperationException ignored) {}
            }
        }
        return null;
    }
}
