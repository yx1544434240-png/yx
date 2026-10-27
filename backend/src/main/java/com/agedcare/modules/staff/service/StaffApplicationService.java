package com.agedcare.modules.staff.service;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.OrderAccessPolicy;
import com.agedcare.modules.staff.dto.StaffApplicationDTO;
import com.agedcare.modules.staff.entity.*;
import com.agedcare.modules.staff.mapper.*;
import com.agedcare.modules.user.entity.SysUser;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service @RequiredArgsConstructor
public class StaffApplicationService {
    private final StaffApplicationMapper applications;
    private final StaffProfileMapper profiles;
    private final SysUserMapper users;

    private void admin() { if (!"ADMIN".equals(OrderAccessPolicy.user().getRole())) throw new BusinessException(403, "仅管理员可审核入驻申请"); }
    private SysUser lockUser(Long id) {
        var user = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, id).last("FOR UPDATE"));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException("申请账号不存在或已停用");
        return user;
    }
    public StaffApplication mine() {
        return applications.selectOne(new LambdaQueryWrapper<StaffApplication>().eq(StaffApplication::getUserId, OrderAccessPolicy.user().getId()));
    }
    @Transactional(rollbackFor=Exception.class)
    public void submit(StaffApplicationDTO dto) {
        Long owner = OrderAccessPolicy.user().getId();
        SysUser user = lockUser(owner);
        if (!"RESIDENT".equals(user.getRole())) throw new BusinessException(403, "仅居民账号可申请成为服务人员");
        if (profiles.selectCount(new LambdaQueryWrapper<StaffProfile>().eq(StaffProfile::getUserId, owner)) > 0) throw new BusinessException("账号已有人员档案，请联系社区管理员");
        StaffApplication previous = mine();
        var now = LocalDateTime.now();
        if (previous == null) {
            applications.insert(StaffApplication.builder().userId(owner).realName(dto.getRealName().trim()).phone(dto.getPhone())
                    .serviceArea(dto.getServiceArea().trim()).introduction(dto.getIntroduction().trim()).status("PENDING").revision(1)
                    .submittedAt(now).createdAt(now).updatedAt(now).build());
        } else {
            if (!"REJECTED".equals(previous.getStatus())) throw new BusinessException("申请已提交，请查看审核结果，不要重复申请");
            int changed = applications.update(null, new LambdaUpdateWrapper<StaffApplication>().eq(StaffApplication::getId, previous.getId())
                    .eq(StaffApplication::getStatus, "REJECTED").eq(StaffApplication::getRevision, dto.getRevision())
                    .set(StaffApplication::getRealName, dto.getRealName().trim()).set(StaffApplication::getPhone, dto.getPhone())
                    .set(StaffApplication::getServiceArea, dto.getServiceArea().trim()).set(StaffApplication::getIntroduction, dto.getIntroduction().trim())
                    .set(StaffApplication::getStatus, "PENDING").set(StaffApplication::getRevision, dto.getRevision()+1).set(StaffApplication::getSubmittedAt, now)
                    .set(StaffApplication::getUpdatedAt, now).set(StaffApplication::getAuditReason, null).set(StaffApplication::getAuditUserId, null).set(StaffApplication::getAuditTime, null));
            if (changed == 0) throw new BusinessException("申请已更新，请刷新后重试");
        }
    }
    public Page<StaffApplication> page(int page, int size, String status) {
        admin();
        if (status != null && !Set.of("PENDING", "APPROVED", "REJECTED").contains(status)) throw new BusinessException("审核状态无效");
        var query = new LambdaQueryWrapper<StaffApplication>().orderByDesc(StaffApplication::getSubmittedAt);
        if (status != null) query.eq(StaffApplication::getStatus, status);
        return applications.selectPage(new Page<>(page, size), query);
    }
    @Transactional(rollbackFor=Exception.class)
    public void audit(Long id, int revision, boolean approve, String reason, String station, BigDecimal longitude, BigDecimal latitude) {
        admin();
        if (reason != null && reason.length() > 200) throw new BusinessException("审核意见不能超过200字");
        if (!approve && (reason == null || reason.isBlank())) throw new BusinessException("退回时请说明原因");
        if (approve && (station == null || station.isBlank() || station.length() > 100 || longitude == null || latitude == null
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0 || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0))
            throw new BusinessException("通过前请配置有效的服务站点及经纬度");
        var application = applications.selectById(id);
        if (application == null) throw new BusinessException("入驻申请不存在");
        var user = lockUser(application.getUserId());
        if (!"RESIDENT".equals(user.getRole())) throw new BusinessException("该账号身份已变化，请刷新后核对");
        var now = LocalDateTime.now();
        int changed = applications.update(null, new LambdaUpdateWrapper<StaffApplication>().eq(StaffApplication::getId, id)
                .eq(StaffApplication::getStatus, "PENDING").eq(StaffApplication::getRevision, revision)
                .set(StaffApplication::getStatus, approve ? "APPROVED" : "REJECTED").set(StaffApplication::getAuditReason, reason == null ? "" : reason.trim())
                .set(StaffApplication::getAuditUserId, OrderAccessPolicy.user().getId()).set(StaffApplication::getAuditTime, now).set(StaffApplication::getUpdatedAt, now));
        if (changed == 0) throw new BusinessException("申请已处理或材料已更新，请刷新后审核");
        if (approve) {
            if (users.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, application.getPhone()).ne(SysUser::getId, user.getId())) > 0)
                throw new BusinessException("申请手机号已关联其他账号，请核实后再审核");
            try {
                int updated = users.update(null, new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, user.getId()).eq(SysUser::getRole, "RESIDENT").eq(SysUser::getStatus, 1)
                        .set(SysUser::getRole, "STAFF").set(SysUser::getRealName, application.getRealName()).set(SysUser::getPhone, application.getPhone()).set(SysUser::getUpdatedAt, now));
                if (updated != 1) throw new BusinessException("账号状态已变化，请刷新后审核");
                profiles.insert(StaffProfile.builder().userId(user.getId()).serviceArea(application.getServiceArea()).baseStationName(station.trim())
                        .baseLongitude(longitude).baseLatitude(latitude).currentStatus("RESTING").auditStatus("APPROVED").auditTime(now).auditReason(reason == null ? "" : reason.trim())
                        .priorRating(BigDecimal.valueOf(4)).totalOrders(0).createdAt(now).updatedAt(now).build());
            } catch (org.springframework.dao.DuplicateKeyException e) { throw new BusinessException("账号已有档案或手机号重复，请刷新后核实"); }
        }
    }
}
