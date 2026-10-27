package com.agedcare.common.storage;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.OrderAccessPolicy;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.elder.service.ElderProfileService;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.fulfillment.entity.ServiceRecord;
import com.agedcare.modules.fulfillment.mapper.ServiceRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrivateMediaService {
    private final CosService cosService;
    private final ServiceOrderMapper orders;
    private final ElderProfileMapper elders;
    private final ElderProfileService elderService;
    private final StaffProfileMapper staff;
    private final DispatchAttemptMapper attempts;
    private final ServiceRecordMapper records;
    private final ObjectMapper json;
    @Value("${app.private-media-dir:./uploads/private}")
    private String directory;
    @Value("${app.upload-fallback-dir:./uploads/fallback}")
    private String publicFallback = "./uploads/fallback";
    @Value("${app.service-images-dir:./uploads/service-images}")
    private String publicServices = "./uploads/service-images";

    @jakarta.annotation.PostConstruct
    public void validateDirectories() {
        for(String value:java.util.List.of(publicFallback,publicServices)) {
            Path publicPath=Path.of(value).toAbsolutePath().normalize();
            if(root().startsWith(publicPath) || publicPath.startsWith(root()))
                throw new IllegalStateException("履约照片私有目录必须与公开图片目录完全分离");
        }
    }

    public String upload(MultipartFile file, Long orderId) {
        if (orderId == null) throw new BusinessException("履约照片必须关联订单");
        var order = orders.selectById(orderId);
        OrderAccessPolicy.assigned(order, staff, attempts);
        if (!"IN_SERVICE".equals(order.getStatus())) throw new BusinessException("仅服务中的订单可上传履约照片");
        return save(file, orderId, OrderAccessPolicy.user().getId());
    }

    String importLegacy(byte[] bytes, String name, Long orderId, Long uploaderId) {
        return save(new ByteArrayUpload(bytes,name), orderId, uploaderId);
    }

    private String save(MultipartFile file, Long orderId, Long uploaderId) {
        String extension = cosService.validateUpload(file);
        String relative = orderId + "/" + uploaderId + "/" + UUID.randomUUID() + extension;
        try {
            Path path = root().resolve(relative).normalize();
            Files.createDirectories(path.getParent());
            file.transferTo(path);
        } catch (java.io.IOException e) { throw new BusinessException("履约照片保存失败"); }
        return "/media/private/fulfillment/" + relative;
    }

    public void requireOwned(String value, Long orderId, Long uploaderId) {
        if (value == null || !value.matches("/media/private/fulfillment/[0-9]+/[0-9]+/[a-f0-9-]{36}\\.(jpg|jpeg|png|webp)")
                || !value.startsWith("/media/private/fulfillment/" + orderId + "/" + uploaderId + "/")
                || !Files.isRegularFile(resolve(value), LinkOption.NOFOLLOW_LINKS))
            throw new BusinessException("履约照片必须由当前服务人员为本订单上传");
    }

    public Resource read(Long orderId, Long uploaderId, String filename) {
        String value = "/media/private/fulfillment/" + orderId + "/" + uploaderId + "/" + filename;
        var order = orders.selectById(orderId);
        OrderAccessPolicy.view(order, elders, elderService, staff, attempts);
        requireOwned(value, orderId, uploaderId);
        if (!OrderAccessPolicy.user().getId().equals(uploaderId)) {
            var record = records.selectOne(new LambdaQueryWrapper<ServiceRecord>().eq(ServiceRecord::getOrderId, orderId));
            try {
                boolean attached = false;
                if (record != null && record.getAttachmentsJson() != null) {
                    for (var item : json.readTree(record.getAttachmentsJson())) if (value.equals(item.asText())) attached = true;
                }
                if (!attached) throw new BusinessException(403, "无权查看未提交的履约照片");
            } catch (java.io.IOException e) { throw new BusinessException("履约照片记录损坏"); }
        }
        return new FileSystemResource(resolve(value));
    }

    private Path resolve(String value) { return root().resolve(value.substring("/media/private/fulfillment/".length())).normalize(); }
    private Path root() { return Path.of(directory).toAbsolutePath().normalize(); }
}
