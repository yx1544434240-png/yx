package com.agedcare.modules.family.service;

import com.agedcare.common.enums.FamilyBindingStatus;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.family.dto.ElderSearchVO;
import com.agedcare.modules.family.dto.FamilyBindingVO;
import com.agedcare.modules.family.entity.FamilyBinding;
import com.agedcare.modules.family.mapper.FamilyBindingMapper;
import com.agedcare.modules.user.entity.SysUser;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FamilyBindingService {

    private final FamilyBindingMapper familyBindingMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final SysUserMapper sysUserMapper;

    public ElderSearchVO searchElderByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException("请输入老人联系电话");
        }
        ElderProfile elder = elderProfileMapper.selectOne(new LambdaQueryWrapper<ElderProfile>()
                .eq(ElderProfile::getPhone, phone.trim())
                .eq(ElderProfile::getStatus, 1)
                .last("LIMIT 1"));
        if (elder == null) {
            throw new BusinessException("未找到电话为 " + phone + " 的老人档案");
        }
        return ElderSearchVO.builder()
                .id(elder.getId())
                .name(maskName(elder.getName()))
                .phone(maskPhone(elder.getPhone()))
                .community(elder.getCommunity() != null ? elder.getCommunity() : "")
                .exists(true)
                .build();
    }

    private String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        int len = name.length();
        if (len <= 1) {
            return name;
        }
        if (len == 2) {
            return name.charAt(0) + "*";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(name.charAt(0));
        for (int i = 0; i < len - 2; i++) {
            sb.append("*");
        }
        sb.append(name.charAt(len - 1));
        return sb.toString();
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        if (phone.length() >= 11) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return phone.substring(0, 2) + "****" + phone.substring(phone.length() - 2);
    }

    @Transactional(rollbackFor = Exception.class)
    public FamilyBinding applyBinding(Long elderId, String elderPhone, String elderName, String relation, String proofMaterials) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException("请先登录");
        }

        ElderProfile elder = null;
        if (elderId != null) {
            elder = elderProfileMapper.selectById(elderId);
        } else if (StringUtils.hasText(elderPhone)) {
            elder = elderProfileMapper.selectOne(new LambdaQueryWrapper<ElderProfile>()
                    .eq(ElderProfile::getPhone, elderPhone.trim())
                    .eq(ElderProfile::getStatus, 1)
                    .last("LIMIT 1"));
        }

        if (elder == null) {
            throw new BusinessException("未找到目标老人档案，请核对手机号或先创建老人建档");
        }

        Long existingCount = familyBindingMapper.selectCount(new LambdaQueryWrapper<FamilyBinding>()
                .eq(FamilyBinding::getFamilyUserId, currentUser.getId())
                .eq(FamilyBinding::getElderId, elder.getId())
                .in(FamilyBinding::getStatus, FamilyBindingStatus.PENDING.getCode(), FamilyBindingStatus.APPROVED.getCode()));
        if (existingCount > 0) {
            throw new BusinessException("已存在审核中或已通过的绑定关系，请勿重复申请");
        }

        FamilyBinding binding = FamilyBinding.builder()
                .familyUserId(currentUser.getId())
                .elderId(elder.getId())
                .relation(relation)
                .proofMaterials(proofMaterials != null ? proofMaterials : "")
                .status(FamilyBindingStatus.PENDING.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        familyBindingMapper.insert(binding);
        return binding;
    }

    public List<FamilyBindingVO> getMyBindings() {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException("请先登录");
        }

        List<FamilyBinding> list = familyBindingMapper.selectList(new LambdaQueryWrapper<FamilyBinding>()
                .eq(FamilyBinding::getFamilyUserId, currentUser.getId())
                .orderByDesc(FamilyBinding::getCreatedAt));

        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    public Page<FamilyBindingVO> pageBindings(int pageNum, int pageSize, String status) {
        Page<FamilyBinding> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FamilyBinding> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(FamilyBinding::getStatus, status);
        }
        wrapper.orderByDesc(FamilyBinding::getCreatedAt);

        Page<FamilyBinding> bindingPage = familyBindingMapper.selectPage(page, wrapper);
        List<FamilyBindingVO> voList = bindingPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        Page<FamilyBindingVO> resultPage = new Page<>(bindingPage.getCurrent(), bindingPage.getSize(), bindingPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditBinding(Long bindingId, boolean approve, String reason) {
        LoginUser currentUser = UserContext.getUser();
        FamilyBinding binding = familyBindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new BusinessException("绑定记录不存在");
        }

        if (!FamilyBindingStatus.PENDING.getCode().equals(binding.getStatus())) {
            throw new BusinessException("该申请已处理，无法重复审核");
        }

        binding.setStatus(approve ? FamilyBindingStatus.APPROVED.getCode() : FamilyBindingStatus.REJECTED.getCode());
        binding.setReviewerId(currentUser.getId());
        binding.setReviewTime(LocalDateTime.now());
        binding.setReviewReason(reason != null ? reason : (approve ? "审核通过" : "资料不满足要求"));
        binding.setUpdatedAt(LocalDateTime.now());
        familyBindingMapper.updateById(binding);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeBinding(Long bindingId) {
        LoginUser currentUser = UserContext.getUser();
        FamilyBinding binding = familyBindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new BusinessException("绑定记录不存在");
        }

        if (!UserRole.ADMIN.getCode().equalsIgnoreCase(currentUser.getRole())
                && !binding.getFamilyUserId().equals(currentUser.getId())) {
            throw new BusinessException(403, "无权撤销该绑定");
        }

        binding.setStatus(FamilyBindingStatus.REVOKED.getCode());
        binding.setReviewReason("用户或管理员主动撤销绑定");
        binding.setUpdatedAt(LocalDateTime.now());
        familyBindingMapper.updateById(binding);
    }

    private FamilyBindingVO convertToVO(FamilyBinding binding) {
        FamilyBindingVO vo = new FamilyBindingVO();
        BeanUtils.copyProperties(binding, vo);
        vo.setElder(elderProfileMapper.selectById(binding.getElderId()));
        SysUser user = sysUserMapper.selectById(binding.getFamilyUserId());
        if (user != null) {
            vo.setFamilyUser(com.agedcare.modules.user.dto.UserSummaryVO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .realName(user.getRealName())
                    .phone(user.getPhone())
                    .avatar(user.getAvatar())
                    .role(user.getRole())
                    .build());
        }
        return vo;
    }
}
