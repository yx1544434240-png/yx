package com.agedcare.serviceitem;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.exception.GlobalExceptionHandler;
import com.agedcare.common.security.*;
import com.agedcare.modules.serviceitem.controller.ServiceItemController;
import com.agedcare.modules.serviceitem.service.*;
import com.agedcare.modules.user.entity.SysUser;
import com.agedcare.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.core.env.MapPropertySource;
import com.agedcare.common.config.WebMvcConfig;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class ServiceImageStorageTest {
    @TempDir Path directory;
    @AfterEach void cleanup() { UserContext.clear(); }

    private MockMultipartFile picture() throws Exception {
        var image = new BufferedImage(2000, 20, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("file", "../../untrusted-name.png", "image/png", bytes.toByteArray());
    }
    @Test void storesRealImageUnderRandomNameAndOptimizesDimensions() throws Exception {
        String path = new ServiceImageStorage(directory.toString()).store(picture());
        assertTrue(path.matches("/media/service-images/[0-9a-f-]{36}\\.jpg"));
        Path saved = directory.resolve(path.substring(path.lastIndexOf('/') + 1));
        assertTrue(Files.exists(saved));
        var result = ImageIO.read(saved.toFile());
        assertEquals(1600, result.getWidth());
        assertEquals(16, result.getHeight());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void rejectsSvgDisguisedAsJpeg() {
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "<svg>unsafe</svg>".getBytes());
        assertThrows(BusinessException.class, () -> new ServiceImageStorage(directory.toString()).store(file));
    }
    @Test void rejectsEmptyAndOversizeFiles() {
        var storage = new ServiceImageStorage(directory.toString());
        assertThrows(BusinessException.class, () -> storage.store(new MockMultipartFile("file", new byte[0])));
        assertThrows(BusinessException.class, () -> storage.store(new MockMultipartFile("file", new byte[2 * 1024 * 1024 + 1])));
    }

    private MockMvc mvc(String token, String role) {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        SysUserMapper users = mock(SysUserMapper.class);
        if (token != null) {
            when(jwt.parseToken(token)).thenReturn(LoginUser.builder().id(1L).role(role).build());
            when(users.selectById(1L)).thenReturn(SysUser.builder().id(1L).status(1).role(role).build());
        }
        var controller = new ServiceItemController(mock(ServiceItemService.class), new ServiceImageStorage(directory.toString()));
        return MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new AuthInterceptor(jwt, users, new UploadRateLimiter()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void anonymousCannotUpload() throws Exception {
        mvc(null, null).perform(multipart("/service-items/image").file(picture())).andExpect(jsonPath("$.code").value(401));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
    @Test void residentCannotUpload() throws Exception {
        mvc("resident", "RESIDENT").perform(multipart("/service-items/image").file(picture()).header("Authorization", "Bearer resident"))
                .andExpect(jsonPath("$.code").value(403));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
    @Test void administratorCanUpload() throws Exception {
        mvc("admin", "ADMIN").perform(multipart("/service-items/image").file(picture()).header("Authorization", "Bearer admin"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isString());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Configuration
    @EnableWebMvc
    @Import(WebMvcConfig.class)
    static class ImageWebConfig {
        @Bean ServiceImageStorage storage(@Value("${app.service-images-dir}") String directory) {
            return new ServiceImageStorage(directory);
        }
        @Bean AuthInterceptor auth() {
            return new AuthInterceptor(mock(JwtTokenProvider.class), mock(SysUserMapper.class), new UploadRateLimiter());
        }
    }

    @Test void defaultsAndUploadedPhotosArePubliclyServedAsJpeg() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("image-test",
                    java.util.Map.of("app.service-images-dir", directory.resolve("new-storage").toString())));
            context.register(ImageWebConfig.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var result = mvc.perform(get("/media/service-images/meal.jpg"))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertEquals((byte) 0xff, result[0]);
            String uploaded = context.getBean(ServiceImageStorage.class).store(picture());
            mvc.perform(get(uploaded)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        }
    }
}
