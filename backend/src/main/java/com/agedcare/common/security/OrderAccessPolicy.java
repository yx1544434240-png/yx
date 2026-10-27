package com.agedcare.common.security;

import com.agedcare.common.api.ResultCode;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.elder.service.ElderProfileService;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Objects;

/** 订单对象权限集中校验，未知角色默认拒绝。 */
public final class OrderAccessPolicy {
    private OrderAccessPolicy() {}

    public static LoginUser user() {
        LoginUser user = UserContext.getUser();
        if (user == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        return user;
    }

    public static void customer(ServiceOrder order, ElderProfileMapper elders, ElderProfileService profiles, boolean allowAdmin) {
        LoginUser user = user();
        if (order == null) throw new BusinessException("订单不存在");
        if (allowAdmin && "ADMIN".equals(user.getRole())) return;
        if (!allowAdmin && "ADMIN".equals(user.getRole())) throw new BusinessException(403, "管理员不得代用户评价或投诉");
        if (!"RESIDENT".equals(user.getRole())) throw new BusinessException(ResultCode.FORBIDDEN);
        if (Objects.equals(order.getRequesterUserId(), user.getId())) return;
        var elder = elders.selectById(order.getElderId());
        if (elder == null) throw new BusinessException(ResultCode.FORBIDDEN);
        profiles.checkAccessPermission(elder);
    }

    public static void view(ServiceOrder order, ElderProfileMapper elders, ElderProfileService profiles,
                            StaffProfileMapper staff, DispatchAttemptMapper attempts) {
        if (!"STAFF".equals(user().getRole())) {
            customer(order, elders, profiles, true);
            return;
        }
        if (order == null) throw new BusinessException("订单不存在");
        var member = staff.selectOne(new LambdaQueryWrapper<StaffProfile>().eq(StaffProfile::getUserId, user().getId()));
        if (member == null) throw new BusinessException(ResultCode.FORBIDDEN);
        Long count = attempts.selectCount(new LambdaQueryWrapper<DispatchAttempt>()
                .eq(DispatchAttempt::getOrderId, order.getId()).eq(DispatchAttempt::getStaffId, member.getId())
                .in(DispatchAttempt::getStatus, "PENDING", "ACCEPTED"));
        if (count == null || count == 0) throw new BusinessException(ResultCode.FORBIDDEN);
    }

    public static void assigned(ServiceOrder order, StaffProfileMapper staff, DispatchAttemptMapper attempts) {
        if (!"STAFF".equals(user().getRole()) || order == null || order.getCurrentDispatchId() == null)
            throw new BusinessException(ResultCode.FORBIDDEN);
        var member = staff.selectOne(new LambdaQueryWrapper<StaffProfile>().eq(StaffProfile::getUserId, user().getId()));
        var attempt = attempts.selectById(order.getCurrentDispatchId());
        if (member == null || attempt == null || !Objects.equals(member.getId(), attempt.getStaffId())
                || !"ACCEPTED".equals(attempt.getStatus())) throw new BusinessException(ResultCode.FORBIDDEN);
    }
}
