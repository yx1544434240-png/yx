package com.agedcare.common.storage;
import com.agedcare.modules.fulfillment.entity.ServiceRecord;
import com.agedcare.modules.fulfillment.mapper.ServiceRecordMapper;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.elder.service.ElderProfileService;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.common.config.DispatchClusterLock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

class LegacyPhotoMigrationTest {
    @TempDir Path directory;
    @Test void copiesAndCommitsPrivateReferenceBeforeDeletingPublicSourceAndIsIdempotent() throws Exception {
        var cos=new CosService(); ReflectionTestUtils.setField(cos,"fallbackDir",directory.resolve("public").toString());
        String key="fulfillment/20261021/0123456789abcdef0123456789abcdef.png";
        Path original=directory.resolve("public").resolve(key); Files.createDirectories(original.getParent());
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",original.toFile());
        var records=mock(ServiceRecordMapper.class); var staff=mock(StaffProfileMapper.class); var jdbc=mock(JdbcTemplate.class);
        var record=ServiceRecord.builder().id(1L).orderId(8L).staffId(4L).attachmentsJson("[\"/media/fallback/"+key+"\"]").build();
        when(records.selectOne(any())).thenReturn(record);
        when(staff.selectById(4L)).thenReturn(StaffProfile.builder().userId(2L).build());
        var media=new PrivateMediaService(cos,mock(ServiceOrderMapper.class),mock(ElderProfileMapper.class),mock(ElderProfileService.class),staff,mock(DispatchAttemptMapper.class),records,new ObjectMapper());
        ReflectionTestUtils.setField(media,"directory",directory.resolve("private").toString());
        var manager=new CountingManager();
        var migration=new LegacyPhotoMigration(records,staff,media,cos,new ImageUrlPolicy(),jdbc,new ObjectMapper(),new TransactionTemplate(manager),mock(DispatchClusterLock.class));
        migration.migrate(1L);
        assertEquals(1,manager.commits);
        assertTrue(Files.exists(original),"事务提交前后应保留源文件，直到独立清理阶段");
        String path=new ObjectMapper().readTree(record.getAttachmentsJson()).get(0).asText();
        assertTrue(path.startsWith("/media/private/fulfillment/8/2/"));
        media.requireOwned(path,8L,2L);
        migration.migrate(1L);
        verify(records,times(1)).updateById(record);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id",7L,"details","[\"local:"+key+"\"]")));
        migration.cleanup();
        assertFalse(Files.exists(original));
        media.requireOwned(path,8L,2L);
        verify(jdbc).update("UPDATE operation_log SET action='LEGACY_PHOTO_CLEANED' WHERE id=?",7L);
    }
    @Test void unknownSourceNeverBecomesAnArbitraryNetworkFetch() {
        var cos=new CosService();
        assertThrows(RuntimeException.class,()->cos.readLegacy("https://evil.example/photo.jpg",new ImageUrlPolicy()));
        assertThrows(RuntimeException.class,()->cos.deleteLegacy("local:fulfillment/../../outside.png"));
    }
    @Test void failedPublicCleanupRemainsPendingForRetry() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id",7L,"details","[\"cos:fulfillment/20261021/0123456789abcdef0123456789abcdef.jpg\"]")));
        var migration=new LegacyPhotoMigration(mock(ServiceRecordMapper.class),mock(StaffProfileMapper.class),mock(PrivateMediaService.class),new CosService(),new ImageUrlPolicy(),jdbc,new ObjectMapper(),new TransactionTemplate(new CountingManager()),mock(DispatchClusterLock.class));
        assertThrows(IllegalStateException.class,migration::cleanup);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    static class CountingManager extends AbstractPlatformTransactionManager {
        int commits;
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object t,TransactionDefinition d){}
        protected void doCommit(DefaultTransactionStatus s){commits++;}
        protected void doRollback(DefaultTransactionStatus s){}
    }
}
