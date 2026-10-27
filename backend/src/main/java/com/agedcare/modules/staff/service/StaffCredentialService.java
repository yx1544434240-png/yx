package com.agedcare.modules.staff.service;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.OrderAccessPolicy;
import com.agedcare.common.storage.CredentialStorage;
import com.agedcare.modules.staff.dto.CredentialSubmitDTO;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.entity.StaffSkill;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.staff.mapper.StaffSkillMapper;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StaffCredentialService {
    private final StaffProfileMapper profiles;
    private final StaffSkillMapper skills;
    private final SysUserMapper users;
    private final CredentialStorage storage;
    private final ObjectMapper json;
    private static final Set<String> CODES = Set.of("COOKING", "CLEANING", "ERRAND", "ESCORT", "NURSING");
    private StaffProfile myProfile() {
        var user = OrderAccessPolicy.user();
        if (!"STAFF".equals(user.getRole())) throw new BusinessException(403, "仅服务人员可提交本人的资质");
        var profile = profiles.selectOne(new LambdaQueryWrapper<StaffProfile>().eq(StaffProfile::getUserId, user.getId()));
        if (profile == null) throw new BusinessException("服务人员档案不存在，请联系管理员");
        return profile;
    }
    private void requireAdmin() { if (!"ADMIN".equals(OrderAccessPolicy.user().getRole())) throw new BusinessException(403, "仅管理员可审核资质"); }
    @Transactional(rollbackFor=Exception.class)
    public void submit(CredentialSubmitDTO dto) {
        StaffProfile profile = myProfile();
        if (!CODES.contains(dto.getSkillCode())) throw new BusinessException("请选择有效的技能分类");
        if (dto.getProofUrls() == null || dto.getProofUrls().isEmpty() || dto.getProofUrls().size() > 3 || dto.getProofUrls().stream().anyMatch(java.util.Objects::isNull) || Set.copyOf(dto.getProofUrls()).size() != dto.getProofUrls().size())
            throw new BusinessException("请上传1至3张不同的证明图片");
        for (String url : dto.getProofUrls()) storage.requireOwned(url, profile.getUserId());
        String proofs;
        try { proofs = json.writeValueAsString(dto.getProofUrls()); } catch (Exception e) { throw new BusinessException("证明材料格式异常"); }
        LocalDateTime now = LocalDateTime.now();
        if (dto.getId() != null) {
            StaffSkill previous = skills.selectById(dto.getId());
            if (previous == null || !profile.getId().equals(previous.getStaffId())) throw new BusinessException(403, "只能修改本人的资质申请");
            if (!"REJECTED".equals(previous.getAuditStatus()) || !previous.getSkillCode().equals(dto.getSkillCode())) throw new BusinessException("仅退回的申请可补充材料，技能分类不能修改");
            int changed = skills.update(null, new LambdaUpdateWrapper<StaffSkill>().eq(StaffSkill::getId, dto.getId())
                    .eq(StaffSkill::getStaffId, profile.getId()).eq(StaffSkill::getAuditStatus, "REJECTED").eq(StaffSkill::getRevision, dto.getRevision())
                    .set(StaffSkill::getSkillName, dto.getSkillName().trim()).set(StaffSkill::getCertificateNo, dto.getCertificateNo().trim())
                    .set(StaffSkill::getProofUrlsJson, proofs).set(StaffSkill::getAuditStatus, "PENDING")
                    .set(StaffSkill::getSubmittedAt, now).set(StaffSkill::getRevision, dto.getRevision()+1)
                    .set(StaffSkill::getAuditReason, null).set(StaffSkill::getAuditTime, null).set(StaffSkill::getAuditUserId, null));
            if (changed == 0) throw new BusinessException("申请状态已变化，请刷新后重试");
        } else {
            if (skills.selectCount(new LambdaQueryWrapper<StaffSkill>().eq(StaffSkill::getStaffId, profile.getId()).eq(StaffSkill::getSkillCode, dto.getSkillCode())) > 0)
                throw new BusinessException("该技能已有资质记录，请查看审核状态或补充被退回的申请");
            try {
                skills.insert(StaffSkill.builder().staffId(profile.getId()).skillCode(dto.getSkillCode()).skillName(dto.getSkillName().trim())
                        .certificateNo(dto.getCertificateNo().trim()).proofUrlsJson(proofs).auditStatus("PENDING").revision(1).submittedAt(now).createdAt(now).build());
            } catch (org.springframework.dao.DuplicateKeyException e) { throw new BusinessException("该技能申请已提交，请刷新查看"); }
        }
    }
    @Transactional(rollbackFor=Exception.class)
    public void audit(Long id, int revision, boolean approve, String reason) {
        requireAdmin();
        if (reason != null && reason.length() > 200) throw new BusinessException("审核意见不能超过200字");
        if (!approve && (reason == null || reason.trim().isEmpty())) throw new BusinessException("退回时必须填写原因");
        int changed = skills.update(null, new LambdaUpdateWrapper<StaffSkill>().eq(StaffSkill::getId, id)
                .eq(StaffSkill::getAuditStatus, "PENDING").eq(StaffSkill::getRevision, revision)
                .set(StaffSkill::getAuditStatus, approve ? "APPROVED" : "REJECTED")
                .set(StaffSkill::getAuditReason, reason == null ? "" : reason.trim())
                .set(StaffSkill::getAuditUserId, OrderAccessPolicy.user().getId()).set(StaffSkill::getAuditTime, LocalDateTime.now()));
        if (changed == 0) throw new BusinessException("申请已处理或材料已更新，请刷新后审核");
    }
    public StaffSkill detail(Long id) {
        var skill = skills.selectById(id);
        if (skill == null) throw new BusinessException("资质申请不存在");
        var user = OrderAccessPolicy.user();
        var profile = profiles.selectById(skill.getStaffId());
        if (profile == null) throw new BusinessException("人员档案不存在");
        if (!"ADMIN".equals(user.getRole()) && !("STAFF".equals(user.getRole()) && profile.getUserId().equals(user.getId()))) throw new BusinessException(403, "无权查看该资质");
        var owner = users.selectById(profile.getUserId());
        skill.setStaffName(owner == null ? "" : owner.getRealName());
        return skill;
    }
    public Page<StaffSkill> page(int pageNum, int pageSize, String status) {
        requireAdmin();
        if (status != null && !Set.of("PENDING", "APPROVED", "REJECTED").contains(status)) throw new BusinessException("无效的审核状态");
        var query = new LambdaQueryWrapper<StaffSkill>().isNotNull(StaffSkill::getSubmittedAt).orderByDesc(StaffSkill::getSubmittedAt);
        if (status != null) query.eq(StaffSkill::getAuditStatus, status);
        var page = skills.selectPage(new Page<StaffSkill>(pageNum, pageSize), query);
        for (var row : page.getRecords()) {
            var profile = profiles.selectById(row.getStaffId());
            var owner = profile == null ? null : users.selectById(profile.getUserId());
            row.setStaffName(owner == null ? "" : owner.getRealName());
        }
        return page;
    }
}
