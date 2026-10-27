package com.agedcare.staff;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.*;
import com.agedcare.modules.staff.dto.StaffApplicationDTO;
import com.agedcare.modules.staff.entity.*;
import com.agedcare.modules.staff.mapper.*;
import com.agedcare.modules.staff.service.StaffApplicationService;
import com.agedcare.modules.user.entity.SysUser;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class StaffApplicationTest {
    final StaffApplicationMapper applications = mock(StaffApplicationMapper.class);
    final StaffProfileMapper profiles = mock(StaffProfileMapper.class);
    final SysUserMapper users = mock(SysUserMapper.class);
    final StaffApplicationService service = new StaffApplicationService(applications, profiles, users);
    @BeforeAll static void mappings() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "staff-application-test");
        for (Class<?> type : List.of(StaffApplication.class, StaffProfile.class, SysUser.class)) TableInfoHelper.initTableInfo(assistant, type);
    }
    @BeforeEach void setup() {
        login(2L, "RESIDENT");
        when(users.selectOne(any())).thenReturn(SysUser.builder().id(2L).role("RESIDENT").status(1).build());
    }
    @AfterEach void clear() { UserContext.clear(); }
    void login(Long id, String role) { UserContext.setUser(LoginUser.builder().id(id).role(role).build()); }
    StaffApplicationDTO form() {
        var form = new StaffApplicationDTO(); form.setRealName(" 张三 "); form.setPhone("13812345678");
        form.setServiceArea("阳光花园社区"); form.setIntroduction("有社区照护经历"); return form;
    }
    @Test void applicantRemainsResidentUntilAdminApproves() {
        service.submit(form());
        var capture = ArgumentCaptor.forClass(StaffApplication.class); verify(applications).insert(capture.capture());
        assertEquals(2L, capture.getValue().getUserId()); assertEquals("PENDING", capture.getValue().getStatus());
        assertEquals(1, capture.getValue().getRevision()); assertEquals("张三", capture.getValue().getRealName());
        verify(users, never()).update(isNull(), any()); verify(profiles, never()).insert(any(StaffProfile.class));
    }
    @Test void duplicatePendingSubmissionAndStaleResubmissionAreRejected() {
        var row = StaffApplication.builder().id(20L).userId(2L).status("PENDING").revision(1).build();
        when(applications.selectOne(any())).thenReturn(row);
        assertThrows(BusinessException.class, () -> service.submit(form()));
        row.setStatus("REJECTED"); assertThrows(BusinessException.class, () -> service.submit(form()));
        when(applications.update(isNull(), any())).thenReturn(1);
        var dto = form(); dto.setRevision(1); service.submit(dto);
        verify(applications, never()).insert(any(StaffApplication.class));
    }
    @Test void auditRequiresAdminReasonAndStationCoordinates() {
        assertThrows(BusinessException.class, () -> service.audit(20L, 1, true, null, "站点", BigDecimal.ONE, BigDecimal.ONE));
        login(1L,"ADMIN");
        assertThrows(BusinessException.class, () -> service.audit(20L, 1, false, " ", null, null, null));
        assertThrows(BusinessException.class, () -> service.audit(20L, 1, true, null, "站点", null, null));
        verifyNoInteractions(applications);
    }
    @Test void approvedApplicationCreatesRestingProfileAndUpdatesAccount() {
        login(1L,"ADMIN");
        when(applications.selectById(20L)).thenReturn(StaffApplication.builder().id(20L).userId(2L).realName("张三").phone("13812345678").serviceArea("阳光花园社区").build());
        when(applications.update(isNull(), any())).thenReturn(1);
        when(users.update(isNull(), any())).thenReturn(1);
        service.audit(20L,1,true,"核验通过","社区服务站",BigDecimal.valueOf(116),BigDecimal.valueOf(39));
        var capture = ArgumentCaptor.forClass(StaffProfile.class); verify(profiles).insert(capture.capture());
        assertEquals(2L, capture.getValue().getUserId()); assertEquals("RESTING", capture.getValue().getCurrentStatus());
        assertEquals("APPROVED", capture.getValue().getAuditStatus()); assertEquals("社区服务站", capture.getValue().getBaseStationName());
    }
    @Test void staleAuditNeverChangesRoleOrCreatesProfile() {
        login(1L,"ADMIN");
        when(applications.selectById(20L)).thenReturn(StaffApplication.builder().id(20L).userId(2L).build());
        assertThrows(BusinessException.class, () -> service.audit(20L,1,true,null,"站点",BigDecimal.ONE,BigDecimal.ONE));
        verify(users, never()).update(isNull(), any()); verify(profiles, never()).insert(any(StaffProfile.class));
    }
    @Test void rejectedApplicationDoesNotGrantStaffRole() {
        login(1L,"ADMIN");
        when(applications.selectById(20L)).thenReturn(StaffApplication.builder().id(20L).userId(2L).build());
        when(applications.update(isNull(), any())).thenReturn(1);
        service.audit(20L,1,false,"请补充经历说明",null,null,null);
        verify(users, never()).update(isNull(), any()); verify(profiles, never()).insert(any(StaffProfile.class));
    }
}
