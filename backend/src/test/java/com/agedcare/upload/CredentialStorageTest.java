package com.agedcare.upload;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.*;
import com.agedcare.common.storage.*;
import com.agedcare.modules.staff.entity.*;
import com.agedcare.modules.staff.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

class CredentialStorageTest {
    @TempDir Path directory;
    final StaffProfileMapper profiles = mock(StaffProfileMapper.class);
    final StaffSkillMapper skills = mock(StaffSkillMapper.class);
    final CredentialStorage storage = new CredentialStorage(new CosService(), profiles, skills, new ObjectMapper());
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(storage, "directory", directory.toString());
        when(profiles.selectOne(any())).thenReturn(StaffProfile.builder().id(10L).userId(2L).build());
        login(2L, "STAFF");
    }
    void login(Long id, String role) { UserContext.setUser(LoginUser.builder().id(id).role(role).build()); }
    @AfterEach void clear() { UserContext.clear(); }
    MockMultipartFile picture() throws Exception {
        var out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "proof.png", "image/png", out.toByteArray());
    }
    @Test void proofIsPrivateOwnerBoundAndAdminCanOnlyReadSubmittedFiles() throws Exception {
        String value = storage.upload(picture()); var filename = value.substring(value.lastIndexOf('/')+1);
        storage.requireOwned(value, 2L); assertTrue(storage.read(2L, filename).exists());
        assertThrows(BusinessException.class, () -> storage.requireOwned(value, 8L));
        login(8L, "STAFF"); assertThrows(BusinessException.class, () -> storage.read(2L, filename));
        login(2L, "RESIDENT"); assertThrows(BusinessException.class, () -> storage.read(2L, filename));
        login(1L, "ADMIN"); assertThrows(BusinessException.class, () -> storage.read(2L, filename));
        when(skills.selectList(any())).thenReturn(List.of(StaffSkill.builder().proofUrlsJson("[\"" + value + "\"]").build()));
        assertTrue(storage.read(2L, filename).exists());
    }
    @Test void rejectsInvalidImagesPublicUrlsAndPathTraversal() {
        assertThrows(BusinessException.class, () -> storage.upload(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())));
        for (String url : List.of("https://example.com/proof.png", "/media/fallback/x.png", "/media/private/credentials/2/../../secret.png"))
            assertThrows(BusinessException.class, () -> storage.requireOwned(url, 2L));
        login(1L, "ADMIN"); assertThrows(BusinessException.class, () -> storage.upload(picture()));
    }
}
