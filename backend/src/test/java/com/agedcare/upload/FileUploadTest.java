package com.agedcare.upload;

import com.agedcare.common.api.Result;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.common.storage.CosService;
import com.agedcare.common.storage.ImageUrlPolicy;
import com.agedcare.modules.upload.controller.FileUploadController;
import com.agedcare.modules.user.controller.UserController;
import com.agedcare.modules.user.dto.UserSummaryVO;
import com.agedcare.modules.user.entity.SysUser;
import com.agedcare.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Map;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileUploadTest {

    @TempDir
    Path tempDir;

    private CosService cosService;
    private FileUploadController fileUploadController;
    private SysUserMapper sysUserMapper;
    private PasswordEncoder passwordEncoder;
    private UserController userController;

    @BeforeEach
    void setUp() {
        cosService = new CosService();
        // 本地存储测试禁止向真实 COS 发出网络请求。
        ReflectionTestUtils.setField(cosService, "secretId", "");
        ReflectionTestUtils.setField(cosService, "secretKey", "");
        ReflectionTestUtils.setField(cosService, "bucket", "test-bucket");
        ReflectionTestUtils.setField(cosService, "region", "ap-guangzhou");
        ReflectionTestUtils.setField(cosService, "fallbackDir", tempDir.toString());
        cosService.init();

        fileUploadController = new FileUploadController(cosService, mock(com.agedcare.common.storage.PrivateMediaService.class), mock(com.agedcare.modules.serviceitem.service.ServiceImageStorage.class));

        sysUserMapper = mock(SysUserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userController = new UserController(sysUserMapper, passwordEncoder, new ImageUrlPolicy());

        UserContext.setUser(LoginUser.builder()
                .id(1001L)
                .username("testresident")
                .realName("测试居民")
                .role("RESIDENT")
                .avatar("http://old-avatar.png")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        cosService.destroy();
    }

    @Test
    void testUploadEmptyFileThrowsException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThrows(BusinessException.class, () -> fileUploadController.uploadImage(emptyFile, "avatar", null));
    }

    @Test
    void testUploadInvalidExtensionThrowsException() {
        MockMultipartFile txtFile = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        assertThrows(BusinessException.class, () -> fileUploadController.uploadImage(txtFile, "avatar", null));
    }

    @Test
    void testUploadAvatarSuccess() {
        MockMultipartFile validImage = picture("png");
        Result<Map<String, String>> result = fileUploadController.uploadImage(validImage, "avatar", null);
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        String url = result.getData().get("url");
        assertNotNull(url);
        assertTrue(url.contains("avatars") || url.contains("fallback"));
    }

    @Test
    void testResidentUploadFulfillmentForbidden() {
        MockMultipartFile validImage = picture("jpg");
        assertThrows(BusinessException.class, () -> fileUploadController.uploadImage(validImage, "fulfillment", 1L));
    }

    @Test
    void testGetUserProfileAndAvatarUpdate() {
        SysUser user = SysUser.builder()
                .id(1001L)
                .username("testresident")
                .realName("测试居民")
                .phone("13800000000")
                .avatar("http://cos.myqcloud.com/old.jpg")
                .role("RESIDENT")
                .build();
        when(sysUserMapper.selectById(1001L)).thenReturn(user);

        Result<UserSummaryVO> profileResult = userController.getProfile();
        assertEquals(200, profileResult.getCode());
        assertEquals("http://cos.myqcloud.com/old.jpg", profileResult.getData().getAvatar());

        UserController.UpdateAvatarDTO dto = new UserController.UpdateAvatarDTO();
        dto.setAvatar("/media/fallback/avatars/1001/20261021/0123456789abcdef0123456789abcdef.jpg");

        Result<String> avatarResult = userController.updateAvatar(dto);
        assertEquals(200, avatarResult.getCode());
        assertEquals(dto.getAvatar(), avatarResult.getData());
        verify(sysUserMapper, atLeastOnce()).updateById(any(SysUser.class));
    }

    @Test void rejectsShortAndMismatchedImageSignatures() {
        for (byte[] bytes : new byte[][]{{1}, {1, 2}, {1, 2, 3}, {1, 2, 3, 4}, {(byte) 0x89, 80, 78, 71, 0, 0, 0, 0}}) {
            assertThrows(BusinessException.class, () -> cosService.uploadImage(
                    new MockMultipartFile("file", "fake.png", "image/png", bytes), "avatars"));
        }
        assertThrows(BusinessException.class, () -> cosService.uploadImage(new MockMultipartFile(
                "file", "disguised.jpg", "image/jpeg", picture("png").getBytes()), "avatars"));
    }

    @Test void fallbackDoesNotUseRequestHost() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServerName("evil.example");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            String relative = cosService.uploadImage(picture("png"), "avatars");
            assertTrue(relative.startsWith("/media/fallback/avatars/"));
            ReflectionTestUtils.setField(cosService, "publicBaseUrl", "https://care.example/api/");
            assertTrue(cosService.uploadImage(picture("png"), "avatars").startsWith("https://care.example/api/media/"));
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    private MockMultipartFile picture(String format) {
        try {
            var out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, out);
            return new MockMultipartFile("file", "picture." + format,
                    "png".equals(format) ? "image/png" : "image/jpeg", out.toByteArray());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
