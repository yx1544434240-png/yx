package com.agedcare.upload;

import com.agedcare.common.storage.*;
import com.agedcare.common.security.*;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.elder.service.ElderProfileService;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.fulfillment.entity.ServiceRecord;
import com.agedcare.modules.fulfillment.mapper.ServiceRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

class PrivateMediaTest {
    @TempDir Path directory;
    final ServiceOrderMapper orders = mock(ServiceOrderMapper.class);
    final ElderProfileMapper elders = mock(ElderProfileMapper.class);
    final ElderProfileService elderService = mock(ElderProfileService.class);
    final StaffProfileMapper staff = mock(StaffProfileMapper.class);
    final DispatchAttemptMapper attempts = mock(DispatchAttemptMapper.class);
    final ServiceRecordMapper records = mock(ServiceRecordMapper.class);
    final PrivateMediaService media = new PrivateMediaService(new CosService(), orders, elders, elderService, staff, attempts, records, new ObjectMapper());
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(media, "directory", directory.toString());
        when(orders.selectById(1L)).thenReturn(ServiceOrder.builder().id(1L).requesterUserId(5L).elderId(201L).status("IN_SERVICE").currentDispatchId(9L).build());
        when(staff.selectOne(any())).thenReturn(StaffProfile.builder().id(401L).userId(2L).build());
        when(attempts.selectById(9L)).thenReturn(DispatchAttempt.builder().id(9L).staffId(401L).status("ACCEPTED").build());
        when(attempts.selectCount(any())).thenReturn(1L);
        UserContext.setUser(LoginUser.builder().id(2L).role("STAFF").build());
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    MockMultipartFile picture() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray());
    }
    @Test void uploadedPhotoIsBoundToOrderAndUploaderAndReadableAfterSubmission() throws Exception {
        String path = media.upload(picture(), 1L);
        assertTrue(path.startsWith("/media/private/fulfillment/1/2/"));
        media.requireOwned(path, 1L, 2L);
        assertThrows(BusinessException.class, () -> media.requireOwned(path, 3L, 2L));
        assertThrows(BusinessException.class, () -> media.requireOwned(path, 1L, 6L));
        var filename = path.substring(path.lastIndexOf('/')+1);
        assertTrue(media.read(1L, 2L, filename).exists());
        UserContext.setUser(LoginUser.builder().id(5L).role("RESIDENT").build());
        assertThrows(BusinessException.class, () -> media.read(1L, 2L, filename));
        when(records.selectOne(any())).thenReturn(ServiceRecord.builder().attachmentsJson("[\""+path+"\"]").build());
        assertTrue(media.read(1L, 2L, filename).exists());
        UserContext.setUser(LoginUser.builder().id(77L).role("RESIDENT").build());
        assertThrows(BusinessException.class, () -> media.read(1L, 2L, filename));
    }
    @Test void rejectsUnassignedUploadsAndTraversal() throws Exception {
        when(attempts.selectById(9L)).thenReturn(DispatchAttempt.builder().staffId(999L).status("ACCEPTED").build());
        assertThrows(BusinessException.class, () -> media.upload(picture(), 1L));
        assertThrows(BusinessException.class, () -> media.requireOwned("/media/private/fulfillment/1/2/../../x.png",1L,2L));
        assertThrows(BusinessException.class, () -> media.upload(picture(), null));
    }
    @Test void refusesPrivateAndPublicDirectoryOverlap() {
        ReflectionTestUtils.setField(media,"publicFallback",directory.toString());
        assertThrows(IllegalStateException.class,media::validateDirectories);
    }
}
