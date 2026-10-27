package com.agedcare.upload;

import com.agedcare.common.storage.ImageUrlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class ImageUrlPolicyTest {
    private ImageUrlPolicy policy() {
        var policy = new ImageUrlPolicy();
        ReflectionTestUtils.setField(policy, "publicBaseUrl", "https://care.example/api");
        ReflectionTestUtils.setField(policy, "bucket", "care-123");
        ReflectionTestUtils.setField(policy, "customDomain", "https://images.example/care");
        return policy;
    }

    @Test void acceptsOnlyConfiguredOriginsAndCorrectImagePurpose() {
        var policy = policy();
        for (String prefix : new String[]{"/media/fallback/", "https://care.example/api/media/fallback/",
                "https://care-123.cos.ap-guangzhou.myqcloud.com/", "https://images.example/care/"}) {
            assertTrue(policy.allows(prefix + "avatars/20261021/test.png", "avatars"), prefix);
            assertTrue(policy.allows(prefix + "fulfillment/20261021/test.jpg", "fulfillment"), prefix);
            assertFalse(policy.allows(prefix + "avatars/20261021/test.png", "fulfillment"), prefix);
        }
    }

    @Test void rejectsUntrustedHostsQueriesTraversalAndProtocolRelativePaths() {
        var policy = policy();
        for (String url : new String[]{"https://evil.com/media/x.png", "https://a.cos.evil.com/x.png",
                "https://evil.com/?.myqcloud.com/", "//evil.com/media/fallback/avatars/x.png",
                "https://care.example.evil.com/api/media/fallback/avatars/x.png",
                "https://care.example@evil.com/api/media/fallback/avatars/x.png",
                "http://care.example/api/media/fallback/avatars/x.png",
                "https://care.example:444/api/media/fallback/avatars/x.png",
                "/media/fallback/avatars/../fulfillment/x.png", "/media/fallback/avatars/%2e%2e/x.png",
                "/media/fallback/avatars/x.png?redirect=https://evil.com", "/media/fallback/avatars/x.png#x",
                "https://other.cos.ap-guangzhou.myqcloud.com/avatars/x.png", "/media/service-images/x.jpg"}) {
            assertFalse(policy.allows(url, "avatars"), url);
        }
    }
    @Test void avatarUpdatesRequireTheUploaderIdentityInThePath() {
        var policy=policy();
        String path="/media/fallback/avatars/5/20261021/0123456789abcdef0123456789abcdef.jpg";
        assertTrue(policy.allowsAvatarFor(path,5L));
        assertFalse(policy.allowsAvatarFor(path,6L));
    }
}
