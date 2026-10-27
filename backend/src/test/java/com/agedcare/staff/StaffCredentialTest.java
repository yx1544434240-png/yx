package com.agedcare.staff;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.*;
import com.agedcare.common.storage.CredentialStorage;
import com.agedcare.modules.staff.dto.CredentialSubmitDTO;
import com.agedcare.modules.staff.entity.*;
import com.agedcare.modules.staff.mapper.*;
import com.agedcare.modules.staff.service.*;
import com.agedcare.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StaffCredentialTest {
    final StaffProfileMapper profiles = mock(StaffProfileMapper.class);
    final StaffSkillMapper skills = mock(StaffSkillMapper.class);
    final SysUserMapper users = mock(SysUserMapper.class);
    final CredentialStorage storage = mock(CredentialStorage.class);
    final StaffCredentialService service = new StaffCredentialService(profiles, skills, users, storage, new ObjectMapper());
    @BeforeAll static void mappings() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "credential-test");
        for (Class<?> type : List.of(StaffSkill.class, StaffProfile.class, StaffShift.class)) TableInfoHelper.initTableInfo(assistant, type);
    }
    @BeforeEach void setup() {
        login(2L, "STAFF");
        when(profiles.selectOne(any())).thenReturn(StaffProfile.builder().id(10L).userId(2L).build());
    }
    @AfterEach void clear() { UserContext.clear(); }
    void login(Long id, String role) { UserContext.setUser(LoginUser.builder().id(id).role(role).build()); }
    CredentialSubmitDTO application() {
        var dto = new CredentialSubmitDTO();
        dto.setSkillCode("NURSING"); dto.setSkillName(" 护理培训证明 "); dto.setCertificateNo(" CERT-01 ");
        dto.setProofUrls(List.of("/media/private/credentials/2/00000000-0000-0000-0000-000000000001.png"));
        return dto;
    }
    @Test void submissionIsPendingAndUsesAuthenticatedStaffRatherThanClientIdentity() {
        var dto = application(); service.submit(dto);
        verify(storage).requireOwned(dto.getProofUrls().get(0), 2L);
        var capture = ArgumentCaptor.forClass(StaffSkill.class); verify(skills).insert(capture.capture());
        var saved = capture.getValue();
        assertEquals(10L, saved.getStaffId()); assertEquals("PENDING", saved.getAuditStatus());
        assertEquals("护理培训证明", saved.getSkillName()); assertEquals("CERT-01", saved.getCertificateNo());
        assertEquals(1, saved.getRevision()); assertNotNull(saved.getSubmittedAt()); assertNull(saved.getAuditTime());
    }
    @Test void rejectsNonStaffMissingMaterialsAndDuplicateApplications() {
        login(3L, "RESIDENT"); assertThrows(BusinessException.class, () -> service.submit(application()));
        login(2L, "STAFF"); var dto = application(); dto.setProofUrls(List.of());
        assertThrows(BusinessException.class, () -> service.submit(dto));
        dto.setProofUrls(List.of("same", "same")); assertThrows(BusinessException.class, () -> service.submit(dto));
        when(skills.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.submit(application()));
        verify(skills, never()).insert(any(StaffSkill.class));
    }
    @Test void onlyOwnedRejectedApplicationsCanBeResubmitted() {
        var dto = application(); dto.setId(21L); dto.setRevision(1);
        var prior = StaffSkill.builder().id(21L).staffId(11L).skillCode("NURSING").auditStatus("REJECTED").revision(1).build();
        when(skills.selectById(21L)).thenReturn(prior);
        assertThrows(BusinessException.class, () -> service.submit(dto));
        prior.setStaffId(10L);
        for (String status : List.of("APPROVED", "PENDING")) {
            prior.setAuditStatus(status); assertThrows(BusinessException.class, () -> service.submit(dto));
        }
        prior.setAuditStatus("REJECTED");
        when(skills.update(isNull(), any())).thenReturn(1, 0);
        service.submit(dto);
        assertThrows(BusinessException.class, () -> service.submit(dto));
    }
    @Test void auditRequiresAdminReasonAndCurrentRevision() {
        assertThrows(BusinessException.class, () -> service.audit(21L, 1, true, null));
        login(1L, "ADMIN");
        assertThrows(BusinessException.class, () -> service.audit(21L, 1, false, " "));
        when(skills.update(isNull(), any())).thenReturn(1, 0);
        service.audit(21L, 2, false, "证书编号不清晰");
        @SuppressWarnings("unchecked") ArgumentCaptor<LambdaUpdateWrapper<StaffSkill>> capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(skills).update(isNull(), capture.capture());
        var update = capture.getValue();
        assertTrue(update.getSqlSegment().contains("revision"));
        assertTrue(update.getParamNameValuePairs().containsValue(2));
        assertTrue(update.getParamNameValuePairs().containsValue("REJECTED"));
        assertThrows(BusinessException.class, () -> service.audit(21L, 2, true, null));
    }
    @Test void anotherStaffCannotReadTheApplication() {
        when(skills.selectById(21L)).thenReturn(StaffSkill.builder().id(21L).staffId(10L).build());
        when(profiles.selectById(10L)).thenReturn(StaffProfile.builder().id(10L).userId(2L).build());
        login(9L, "STAFF"); assertThrows(BusinessException.class, () -> service.detail(21L));
        login(2L, "RESIDENT"); assertThrows(BusinessException.class, () -> service.detail(21L));
        login(2L, "STAFF"); assertEquals(21L, service.detail(21L).getId());
        login(1L, "ADMIN"); assertEquals(21L, service.detail(21L).getId());
    }
    @Test void publicStaffDetailOnlyReturnsApprovedSkillNamesWithoutCertificates() {
        when(profiles.selectById(10L)).thenReturn(StaffProfile.builder().id(10L).userId(2L).build());
        when(skills.selectList(any())).thenReturn(List.of(
                StaffSkill.builder().skillCode("NURSING").skillName("护理").certificateNo("PRIVATE").proofUrlsJson("[]").auditStatus("APPROVED").build(),
                StaffSkill.builder().skillCode("ESCORT").skillName("陪护").auditStatus("PENDING").build()));
        var staffService = new StaffService(profiles, skills, mock(StaffShiftMapper.class), users, null, null, null);
        login(3L, "RESIDENT"); var visible = staffService.getStaffDetail(10L).getSkills();
        assertEquals(1, visible.size()); assertEquals("护理", visible.get(0).getSkillName());
        assertNull(visible.get(0).getCertificateNo()); assertNull(visible.get(0).getProofUrlsJson());
        login(2L, "STAFF"); assertEquals(2, staffService.getStaffDetail(10L).getSkills().size());
    }
    @Test void dtoRequiresCertificateAndOneToThreeImages() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator(); var dto = application();
            assertTrue(validator.validate(dto).isEmpty());
            dto.setCertificateNo(" "); assertFalse(validator.validate(dto).isEmpty());
            dto.setCertificateNo("C"); dto.setProofUrls(List.of()); assertFalse(validator.validate(dto).isEmpty());
            dto.setProofUrls(List.of("a", "b", "c", "d")); assertFalse(validator.validate(dto).isEmpty());
        }
    }
}
