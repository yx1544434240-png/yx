package com.agedcare.modules.staff.service;

import com.agedcare.common.api.ResultCode;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.staff.dto.StaffDetailVO;
import com.agedcare.modules.staff.dto.StaffLocationReportDTO;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.entity.StaffShift;
import com.agedcare.modules.staff.entity.StaffSkill;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.staff.mapper.StaffShiftMapper;
import com.agedcare.modules.staff.mapper.StaffSkillMapper;
import com.agedcare.modules.user.entity.SysUser;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffProfileMapper staffProfileMapper;
    private final StaffSkillMapper staffSkillMapper;
    private final StaffShiftMapper staffShiftMapper;
    private final SysUserMapper sysUserMapper;
    private final ServiceOrderMapper serviceOrderMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final DispatchAttemptMapper dispatchAttemptMapper;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    public Page<StaffDetailVO> pageStaff(int pageNum, int pageSize, String name, String auditStatus, String currentStatus) {
        Page<StaffProfile> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<StaffProfile> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(auditStatus)) {
            wrapper.eq(StaffProfile::getAuditStatus, auditStatus);
        }
        if (StringUtils.hasText(currentStatus)) {
            wrapper.eq(StaffProfile::getCurrentStatus, currentStatus);
        }
        wrapper.orderByDesc(StaffProfile::getCreatedAt);

        Page<StaffProfile> profilePage = staffProfileMapper.selectPage(page, wrapper);
        List<StaffDetailVO> voList = profilePage.getRecords().stream()
                .map(this::assembleStaffDetail)
                .filter(vo -> {
                    if (StringUtils.hasText(name)) {
                        return vo.getUser() != null && vo.getUser().getRealName().contains(name);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        Page<StaffDetailVO> resultPage = new Page<>(profilePage.getCurrent(), profilePage.getSize(), profilePage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    public StaffDetailVO getStaffDetail(Long staffId) {
        StaffProfile profile = staffProfileMapper.selectById(staffId);
        if (profile == null) {
            throw new BusinessException("服务人员档案不存在");
        }
        return assembleStaffDetail(profile);
    }

    public StaffDetailVO getMyStaffProfile() {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException("未登录");
        }
        StaffProfile profile = staffProfileMapper.selectOne(new LambdaQueryWrapper<StaffProfile>()
                .eq(StaffProfile::getUserId, currentUser.getId()));
        if (profile == null) {
            throw new BusinessException("未找到当前账号对应的服务人员档案");
        }
        return assembleStaffDetail(profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStaffStatus(Long staffId, String status) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        if (!Set.of("AVAILABLE", "RESTING", "BUSY").contains(status)) {
            throw new BusinessException("无效的工作状态: " + status);
        }

        StaffProfile profile = staffProfileMapper.selectById(staffId);
        if (profile == null) {
            throw new BusinessException("人员档案不存在");
        }

        // 权限校验：管理员或服务人员本人
        boolean isAdmin = UserRole.ADMIN.getCode().equalsIgnoreCase(currentUser.getRole());
        boolean isSelf = currentUser.getId().equals(profile.getUserId());
        if (!isAdmin && !isSelf) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权修改其他服务人员的工作状态");
        }

        profile.setCurrentStatus(status);
        profile.setUpdatedAt(LocalDateTime.now());
        staffProfileMapper.updateById(profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditStaff(Long staffId, boolean approve, String reason) {
        StaffProfile profile = staffProfileMapper.selectById(staffId);
        if (profile == null) {
            throw new BusinessException("人员不存在");
        }
        profile.setAuditStatus(approve ? "APPROVED" : "REJECTED");
        profile.setAuditReason(reason != null ? reason : "");
        profile.setAuditTime(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        staffProfileMapper.updateById(profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addSkill(Long staffId, String skillCode, String skillName, String certificateNo) {
        Long count = staffSkillMapper.selectCount(new LambdaQueryWrapper<StaffSkill>()
                .eq(StaffSkill::getStaffId, staffId)
                .eq(StaffSkill::getSkillCode, skillCode));
        if (count > 0) {
            throw new BusinessException("该服务人员已具备该技能");
        }

        StaffSkill skill = StaffSkill.builder()
                .staffId(staffId)
                .skillCode(skillCode)
                .skillName(skillName)
                .certificateNo(certificateNo != null ? certificateNo : "")
                .auditStatus("APPROVED")
                .createdAt(LocalDateTime.now())
                .build();
        staffSkillMapper.insert(skill);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeSkill(Long skillId) {
        staffSkillMapper.deleteById(skillId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addShift(StaffShift shift) {
        if (!shift.getEndTime().isAfter(shift.getStartTime())) {
            throw new BusinessException("排班结束时间不能早于开始时间");
        }
        shift.setStatus("ACTIVE");
        shift.setCreatedAt(LocalDateTime.now());
        shift.setUpdatedAt(LocalDateTime.now());
        staffShiftMapper.insert(shift);
    }

    public List<StaffShift> getStaffShifts(Long staffId, LocalDate startDate, LocalDate endDate) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser != null && UserRole.STAFF.getCode().equalsIgnoreCase(currentUser.getRole())) {
            StaffProfile myProfile = staffProfileMapper.selectOne(new LambdaQueryWrapper<StaffProfile>()
                    .eq(StaffProfile::getUserId, currentUser.getId()));
            if (myProfile == null || !myProfile.getId().equals(staffId)) {
                throw new BusinessException(403, "服务人员只能查看本人的排班安排");
            }
        }

        LambdaQueryWrapper<StaffShift> wrapper = new LambdaQueryWrapper<StaffShift>()
                .eq(StaffShift::getStaffId, staffId)
                .eq(StaffShift::getStatus, "ACTIVE");
        if (startDate != null) {
            wrapper.ge(StaffShift::getShiftDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(StaffShift::getShiftDate, endDate);
        }
        wrapper.orderByAsc(StaffShift::getShiftDate).orderByAsc(StaffShift::getStartTime);
        return staffShiftMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reportLocation(StaffLocationReportDTO dto) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        StaffProfile profile = staffProfileMapper.selectOne(new LambdaQueryWrapper<StaffProfile>()
                .eq(StaffProfile::getUserId, currentUser.getId()));
        if (profile == null) {
            throw new BusinessException("未找到服务人员档案");
        }
        profile.setCurrentLongitude(dto.getLongitude());
        profile.setCurrentLatitude(dto.getLatitude());
        profile.setLocationUpdatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        staffProfileMapper.updateById(profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public StaffProfile mockMoveLocation(Long orderId) {
        if (!devMode) {
            throw new BusinessException(403, "模拟移动功能仅在开发/答辩演示模式下开放");
        }

        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        ServiceOrder order = serviceOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!"WAIT_VISIT".equals(order.getStatus()) && !"IN_SERVICE".equals(order.getStatus())) {
            throw new BusinessException("当前订单非待上门/服务中状态，无法执行位置模拟");
        }

        if (order.getCurrentDispatchId() == null) {
            throw new BusinessException("该订单尚未指派服务人员，无法模拟位置");
        }

        DispatchAttempt attempt = dispatchAttemptMapper.selectById(order.getCurrentDispatchId());
        if (attempt == null || attempt.getStaffId() == null) {
            throw new BusinessException("未找到该订单的有效派单记录");
        }

        StaffProfile profile = staffProfileMapper.selectById(attempt.getStaffId());
        if (profile == null) {
            throw new BusinessException("未找到被派工的服务人员档案");
        }

        // 权限校验：必须是系统管理员，或者该订单被指派的服务人员本人
        boolean isAdmin = UserRole.ADMIN.getCode().equalsIgnoreCase(currentUser.getRole());
        boolean isAssignedStaff = currentUser.getId().equals(profile.getUserId());
        if (!isAdmin && !isAssignedStaff) {
            throw new BusinessException(403, "无权对非本人负责的订单执行模拟移动");
        }

        // 获取订单目的地
        BigDecimal destLon = order.getLongitude();
        BigDecimal destLat = order.getLatitude();
        if (destLon == null || destLat == null) {
            ElderProfile elder = elderProfileMapper.selectById(order.getElderId());
            if (elder != null && elder.getLongitude() != null && elder.getLatitude() != null) {
                destLon = elder.getLongitude();
                destLat = elder.getLatitude();
            }
        }
        if (destLon == null || destLat == null) {
            destLon = new BigDecimal("116.407400");
            destLat = new BigDecimal("39.904200");
        }

        BigDecimal currLon = profile.getCurrentLongitude();
        BigDecimal currLat = profile.getCurrentLatitude();
        if (currLon == null || currLat == null) {
            // 初始在目的地西南方约 1.2 公里
            currLon = destLon.subtract(new BigDecimal("0.010000"));
            currLat = destLat.subtract(new BigDecimal("0.008000"));
        } else {
            // 向目的地推进 35%
            BigDecimal diffLon = destLon.subtract(currLon);
            BigDecimal diffLat = destLat.subtract(currLat);
            if (diffLon.abs().compareTo(new BigDecimal("0.0002")) < 0 && diffLat.abs().compareTo(new BigDecimal("0.0002")) < 0) {
                currLon = destLon.subtract(new BigDecimal("0.010000"));
                currLat = destLat.subtract(new BigDecimal("0.008000"));
            } else {
                currLon = currLon.add(diffLon.multiply(new BigDecimal("0.35")));
                currLat = currLat.add(diffLat.multiply(new BigDecimal("0.35")));
            }
        }

        profile.setCurrentLongitude(currLon);
        profile.setCurrentLatitude(currLat);
        profile.setLocationUpdatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        staffProfileMapper.updateById(profile);
        return profile;
    }

    private StaffDetailVO assembleStaffDetail(StaffProfile profile) {
        StaffDetailVO vo = new StaffDetailVO();
        vo.setProfile(profile);
        SysUser user = sysUserMapper.selectById(profile.getUserId());
        if (user != null) {
            vo.setUser(com.agedcare.modules.user.dto.UserSummaryVO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .realName(user.getRealName())
                    .phone(user.getPhone())
                    .avatar(user.getAvatar())
                    .role(user.getRole())
                    .build());
        }
        vo.setSkills(staffSkillMapper.selectList(new LambdaQueryWrapper<StaffSkill>()
                .eq(StaffSkill::getStaffId, profile.getId())));
        LoginUser viewer = UserContext.getUser();
        boolean privateView = viewer != null && ("ADMIN".equals(viewer.getRole()) || ("STAFF".equals(viewer.getRole()) && profile.getUserId().equals(viewer.getId())));
        if (!privateView) vo.setSkills(vo.getSkills().stream().filter(skill -> "APPROVED".equals(skill.getAuditStatus()))
                .map(skill -> StaffSkill.builder().skillCode(skill.getSkillCode()).skillName(skill.getSkillName()).auditStatus("APPROVED").build()).toList());
        vo.setShifts(staffShiftMapper.selectList(new LambdaQueryWrapper<StaffShift>()
                .eq(StaffShift::getStaffId, profile.getId())
                .ge(StaffShift::getShiftDate, LocalDate.now())
                .orderByAsc(StaffShift::getShiftDate)));
        return vo;
    }
}
