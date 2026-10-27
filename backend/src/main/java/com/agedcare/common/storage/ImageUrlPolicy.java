package com.agedcare.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/** 只接收本站上传路径或明确配置的存储源，不通过字符串包含关系判断域名。 */
@Component
public class ImageUrlPolicy {
    public boolean allowsAvatarFor(String value, Long userId) {
        return userId != null && allows(value, "avatars") && URI.create(value).getRawPath()
                .matches(".*/avatars/" + userId + "/[0-9]{8}/[a-f0-9]{32}\\.(jpg|jpeg|png|webp)");
    }
    @Value("${app.public-base-url:}")
    private String publicBaseUrl = "";
    @Value("${tencent.cos.domain:}")
    private String customDomain = "";
    @Value("${tencent.cos.bucket:}")
    private String bucket = "";
    @Value("${tencent.cos.region:ap-guangzhou}")
    private String region = "ap-guangzhou";

    public boolean allows(String value, String directory) {
        if (value == null || value.length() > 255 || !("avatars".equals(directory) || "fulfillment".equals(directory))) return false;
        try {
            URI uri = URI.create(value);
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) return false;
            String path = uri.getRawPath();
            // 禁止编码路径、空段、路径穿越及非图片后缀。
            if (path == null || !path.matches("/[a-zA-Z0-9_/-]+\\.(?i:jpg|jpeg|png|webp)")
                    || path.contains("//") || path.contains("..")) return false;
            String localPrefix = "/media/fallback/" + directory + "/";
            if (!uri.isAbsolute()) return uri.getRawAuthority() == null && path.startsWith(localPrefix);
            if (matchesBase(uri, publicBaseUrl, "/media/fallback/" + directory + "/")) return true;
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String cosBase = bucket.isBlank() ? "" : "https://" + bucket + ".cos." + region + ".myqcloud.com";
            return matchesBase(uri, cosBase, "/" + directory + "/")
                    || matchesBase(uri, customDomain, "/" + directory + "/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean matchesBase(URI uri, String base, String prefix) {
        if (base == null || base.isBlank()) return false;
        URI trusted = URI.create(base);
        if (trusted.getHost() == null || trusted.getRawUserInfo() != null
                || trusted.getRawQuery() != null || trusted.getRawFragment() != null) return false;
        String scheme = trusted.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) return false;
        String basePath = trusted.getPath().replaceAll("/+$", "");
        return scheme.equalsIgnoreCase(uri.getScheme()) && trusted.getHost().equalsIgnoreCase(uri.getHost())
                && port(trusted) == port(uri) && uri.getRawPath().startsWith(basePath + prefix);
    }

    private int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }
}
