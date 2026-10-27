package com.agedcare.common.storage;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.OrderAccessPolicy;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.entity.StaffSkill;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.staff.mapper.StaffSkillMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CredentialStorage {
    private final CosService images;
    private final StaffProfileMapper profiles;
    private final StaffSkillMapper skills;
    private final ObjectMapper json;
    @Value("${app.private-media-dir:./uploads/private}") private String directory;
    private static final String PREFIX = "/media/private/credentials/";
    public String upload(MultipartFile file) {
        var user = OrderAccessPolicy.user();
        if (!"STAFF".equals(user.getRole())) throw new BusinessException(403, "仅服务人员可上传资质证明");
        if (profiles.selectOne(new LambdaQueryWrapper<StaffProfile>().eq(StaffProfile::getUserId, user.getId())) == null)
            throw new BusinessException("未找到服务人员档案，请联系管理员");
        String ext = images.validateUpload(file);
        String value = PREFIX + user.getId() + "/" + UUID.randomUUID() + ext;
        try {
            Path target = resolve(value);
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (java.io.IOException e) { throw new BusinessException("资质图片保存失败，请重试"); }
        return value;
    }
    public void requireOwned(String value, Long userId) {
        if (value == null || !value.matches("/media/private/credentials/[0-9]+/[a-f0-9-]{36}\\.(jpg|jpeg|png|webp)")
                || !value.startsWith(PREFIX + userId + "/")
                || !Files.isRegularFile(resolve(value), LinkOption.NOFOLLOW_LINKS))
            throw new BusinessException("证明图片必须由本人上传，且文件仍然有效");
    }
    public Resource read(Long ownerId, String filename) {
        var user = OrderAccessPolicy.user();
        boolean admin = "ADMIN".equals(user.getRole());
        if (!admin && !("STAFF".equals(user.getRole()) && user.getId().equals(ownerId)))
            throw new BusinessException(403, "仅本人及管理员可查看资质证明");
        String value = PREFIX + ownerId + "/" + filename;
        requireOwned(value, ownerId);
        if (admin) {
            StaffProfile profile = profiles.selectOne(new LambdaQueryWrapper<StaffProfile>().eq(StaffProfile::getUserId, ownerId));
            boolean attached = false;
            if (profile != null) for (var skill : skills.selectList(new LambdaQueryWrapper<StaffSkill>().eq(StaffSkill::getStaffId, profile.getId()))) {
                try { if (skill.getProofUrlsJson() != null) for (var item : json.readTree(skill.getProofUrlsJson())) if (value.equals(item.asText())) attached = true; }
                catch (java.io.IOException e) { throw new BusinessException("资质图片记录格式异常"); }
            }
            if (!attached) throw new BusinessException(403, "无法查看尚未提交的证明图片");
        }
        return new FileSystemResource(resolve(value));
    }
    private Path resolve(String value) { return Path.of(directory).toAbsolutePath().normalize().resolve("credentials").resolve(value.substring(PREFIX.length())).normalize(); }
}
