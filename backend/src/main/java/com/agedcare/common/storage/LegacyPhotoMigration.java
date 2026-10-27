package com.agedcare.common.storage;

import com.agedcare.modules.fulfillment.entity.ServiceRecord;
import com.agedcare.modules.fulfillment.mapper.ServiceRecordMapper;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.agedcare.common.config.DispatchClusterLock;
import java.util.*;

/** 持久化后再清理公开副本；operation_log 兼作可重试的清理任务日志。 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="app.media.migrate-legacy",havingValue="true",matchIfMissing=true)
public class LegacyPhotoMigration implements ApplicationRunner {
    private final ServiceRecordMapper records;
    private final StaffProfileMapper staff;
    private final PrivateMediaService media;
    private final CosService cos;
    private final ImageUrlPolicy policy;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final DispatchClusterLock lock;

    public void run(ApplicationArguments ignored) {
        boolean completed = lock.run("media-migration",30,() -> {
            for(var record:records.selectList(new LambdaQueryWrapper<ServiceRecord>().isNotNull(ServiceRecord::getAttachmentsJson))) migrate(record.getId());
            cleanup();
        });
        if(!completed) throw new IllegalStateException("其他实例正在迁移历史照片，请稍后重启");
    }

    void migrate(Long id) {
        transaction.executeWithoutResult(status -> {
            var record=records.selectOne(new LambdaQueryWrapper<ServiceRecord>().eq(ServiceRecord::getId,id).last("FOR UPDATE"));
            try {
                var paths=json.readTree(record.getAttachmentsJson());
                if(!paths.isArray()) throw new IllegalStateException("历史履约照片不是列表");
                var replacements=new ArrayList<String>(); var sources=new ArrayList<String>();
                for(var item:paths) {
                    String value=item.asText();
                    if(value.startsWith("/media/private/fulfillment/")) {replacements.add(value);continue;}
                    var member=staff.selectById(record.getStaffId());
                    if(member==null || member.getUserId()==null) throw new IllegalStateException("历史照片缺少服务人员归属");
                    var original=cos.readLegacy(value,policy);
                    replacements.add(media.importLegacy(original.bytes(),original.source(),record.getOrderId(),member.getUserId()));
                    sources.add(original.source());
                }
                if(sources.isEmpty()) return;
                record.setAttachmentsJson(json.writeValueAsString(replacements)); records.updateById(record);
                jdbc.update("INSERT INTO operation_log (id,operator_id,operator_name,action,target_type,target_id,details,created_at) VALUES (?,0,'SYSTEM','LEGACY_PHOTO_PENDING','SERVICE_RECORD',?,?,CURRENT_TIMESTAMP)",
                        IdWorker.getId(),record.getId(),json.writeValueAsString(sources));
            } catch(java.io.IOException e){throw new IllegalStateException("历史照片迁移失败",e);}
        });
    }

    void cleanup() {
        for(var task:jdbc.queryForList("SELECT id,details FROM operation_log WHERE action='LEGACY_PHOTO_PENDING'")) {
            try {
                for(var source:json.readTree((String)task.get("details"))) cos.deleteLegacy(source.asText());
                jdbc.update("UPDATE operation_log SET action='LEGACY_PHOTO_CLEANED' WHERE id=?",task.get("id"));
            } catch(Exception e){
                // 公开副本未清理完成时不能宣称迁移成功；持久任务在下次启动继续重试。
                throw new IllegalStateException("历史照片已转为私有，但公开副本尚未清理；请检查原存储访问权限后重启",e);
            }
        }
    }
}
