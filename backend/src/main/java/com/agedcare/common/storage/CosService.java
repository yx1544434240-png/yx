package com.agedcare.common.storage;

import com.agedcare.common.exception.BusinessException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.net.URI;

@Slf4j
@Service
public class CosService {

    @Value("${tencent.cos.secret-id:}")
    private String secretId;

    @Value("${tencent.cos.secret-key:}")
    private String secretKey;

    @Value("${tencent.cos.bucket:}")
    private String bucket;

    @Value("${tencent.cos.region:ap-guangzhou}")
    private String region;

    @Value("${tencent.cos.domain:}")
    private String customDomain;

    @Value("${app.upload-fallback-dir:./uploads/fallback}")
    private String fallbackDir;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl = "";

    private COSClient cosClient;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @PostConstruct
    public void init() {
        if (StringUtils.hasText(secretId) && StringUtils.hasText(secretKey)) {
            try {
                COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
                ClientConfig clientConfig = new ClientConfig(new Region(region));
                this.cosClient = new COSClient(cred, clientConfig);
                log.info("腾讯云 COS 客户端初始化成功，存储桶: {}, 区域: {}", bucket, region);
            } catch (Exception e) {
                log.warn("腾讯云 COS 客户端初始化失败，将使用本地降级存储: {}", e.getMessage());
            }
        } else {
            log.warn("未检测到腾讯云 COS 凭据，文件上传将降级为本地存储模式");
        }
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            try {
                cosClient.shutdown();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 上传图片到腾讯云 COS，返回可访问的完整 URL
     *
     * @param file   前端上传的 Multipart 文件
     * @param subDir 子目录路径，如 "avatars" 或 "fulfillment"
     * @return 文件的网络完整 URL
     */
    public String uploadImage(MultipartFile file, String subDir) {
        String ext = validateUpload(file);
        if ("fulfillment".equals(subDir)) throw new BusinessException("履约图片必须使用私有存储");
        return storePublic(file, subDir, ext);
    }

    public String validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传的文件不能为空");
        }

        // 文件大小限制: 5MB
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("上传图片大小不能超过 5MB");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持 JPG、PNG 或 WEBP 格式图片");
        }

        // 校验文件魔数与内容真实性
        validateImageContent(file, ext);
        return ext;
    }

    private String storePublic(MultipartFile file, String subDir, String ext) {
        String dateFolder = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String cleanSubDir = StringUtils.hasText(subDir) ? subDir.replaceAll("[^a-zA-Z0-9_-]", "") : "images";
        String owner = "avatars".equals(cleanSubDir) ? com.agedcare.common.security.OrderAccessPolicy.user().getId() + "/" : "";
        String objectKey = cleanSubDir + "/" + owner + dateFolder + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

        if (cosClient != null && StringUtils.hasText(bucket)) {
            try (InputStream in = file.getInputStream()) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(file.getSize());
                metadata.setContentType(ext.equals(".png") ? "image/png"
                        : ext.equals(".webp") ? "image/webp" : "image/jpeg");

                PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, objectKey, in, metadata);
                cosClient.putObject(putObjectRequest);

                if (StringUtils.hasText(customDomain)) {
                    String domain = customDomain.endsWith("/") ? customDomain.substring(0, customDomain.length() - 1) : customDomain;
                    return domain + "/" + objectKey;
                }

                return "https://" + bucket + ".cos." + region + ".myqcloud.com/" + objectKey;
            } catch (Exception e) {
                log.error("上传至腾讯云 COS 异常，尝试本地降级存储: {}", e.getMessage(), e);
            }
        }

        // 本地降级存储兜底（离线/单元测试/网络异常时保障业务不中断）
        return saveToLocalFallback(file, objectKey);
    }

    private void validateImageContent(MultipartFile file, String ext) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = is.readNBytes(12);
            int read = header.length;
            boolean isJpeg = (read >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF);
            boolean isPng = (read >= 8 && header[0] == (byte) 0x89 && header[1] == (byte) 0x50
                    && header[2] == (byte) 0x4E && header[3] == (byte) 0x47
                    && header[4] == 13 && header[5] == 10 && header[6] == 26 && header[7] == 10);
            boolean isWebp = (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P');

            boolean matchesExtension = ((ext.equals(".jpg") || ext.equals(".jpeg")) && isJpeg)
                    || (ext.equals(".png") && isPng) || (ext.equals(".webp") && isWebp);
            if (!matchesExtension) {
                throw new BusinessException("文件头与图片真实格式不匹配，仅支持真实图片");
            }
        } catch (IOException e) {
            throw new BusinessException("读取上传图片流失败");
        }
    }

    private String saveToLocalFallback(MultipartFile file, String objectKey) {
        try {
            Path targetDir = Path.of(fallbackDir).toAbsolutePath().normalize();
            Path targetFile = targetDir.resolve(objectKey);
            Files.createDirectories(targetFile.getParent());
            file.transferTo(targetFile.toFile());

            String relativePath = "/media/fallback/" + objectKey;
            // 不信任请求 Host/转发头；未配置固定公网入口时只返回相对路径。
            return StringUtils.hasText(publicBaseUrl)
                    ? publicBaseUrl.replaceAll("/+$", "") + relativePath : relativePath;
        } catch (IOException e) {
            log.error("本地降级保存文件失败: {}", e.getMessage(), e);
            throw new BusinessException("图片上传失败，请稍后重试");
        }
    }

    private String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    record LegacyFile(String source, byte[] bytes) {}

    LegacyFile readLegacy(String value, ImageUrlPolicy policy) {
        URI uri;
        try { uri = URI.create(value); } catch (Exception e) { throw new BusinessException("历史图片地址无效"); }
        if (uri.getRawQuery()!=null || uri.getRawFragment()!=null || uri.getRawUserInfo()!=null) throw new BusinessException("历史图片地址包含非法参数");
        String path=uri.getRawPath();
        String localPrefix="/media/fallback/";
        String source;
        if(path!=null && path.contains(localPrefix)) source="local:"+path.substring(path.indexOf(localPrefix)+localPrefix.length());
        else {
            if(!policy.allows(value,"fulfillment")) throw new BusinessException("历史图片不属于配置的存储源，需管理员核验");
            source="cos:"+bucket+":"+path.substring(path.lastIndexOf("/fulfillment/")+1);
        }
        String key=legacyKey(source);
        try {
            byte[] bytes;
            if(source.startsWith("local:")) {
                try(var stream=Files.newInputStream(Path.of(fallbackDir).toAbsolutePath().resolve(key))) {bytes=stream.readNBytes((int)MAX_FILE_SIZE+1);}
            }
            else {
                if(cosClient==null) throw new BusinessException("迁移历史COS履约照片需要配置原存储桶凭据");
                try(var object=cosClient.getObject(bucket,key);var stream=object.getObjectContent()) { bytes=stream.readNBytes((int)MAX_FILE_SIZE+1); }
            }
            if(bytes.length>MAX_FILE_SIZE) throw new BusinessException("历史图片超过5MB，需要人工压缩迁移");
            return new LegacyFile(source,bytes);
        } catch(IOException e){throw new BusinessException("读取历史履约照片失败，请核查旧存储目录");}
    }

    void deleteLegacy(String source) {
        String key=legacyKey(source);
        try {
            if(source.startsWith("local:")) Files.deleteIfExists(Path.of(fallbackDir).toAbsolutePath().resolve(key));
            else {
                if(cosClient==null) throw new BusinessException("清理历史COS照片需要配置存储桶凭据");
                cosClient.deleteObject(bucket,key);
            }
        } catch(IOException e){throw new BusinessException("清理历史履约照片失败");}
    }

    private String legacyKey(String source) {
        if(source==null) throw new BusinessException("历史照片来源为空");
        String key;
        if(source.startsWith("local:")) key=source.substring(6);
        else if(source.startsWith("cos:")) {
            int separator=source.indexOf(':',4);
            if(separator<0 || !java.util.Objects.equals(source.substring(4,separator),bucket))
                throw new BusinessException("清理任务需要原COS存储桶配置，禁止向其他存储桶删除文件");
            key=source.substring(separator+1);
        } else throw new BusinessException("未知历史照片来源");
        if(!key.matches("fulfillment/[0-9]{8}/[a-fA-F0-9-]{32,36}\\.(?i:jpg|jpeg|png|webp)"))
            throw new BusinessException("历史照片路径不符合上传规则，禁止迁移或删除");
        return key;
    }
}
