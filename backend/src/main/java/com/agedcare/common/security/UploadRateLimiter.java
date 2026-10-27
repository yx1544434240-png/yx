package com.agedcare.common.security;

import com.agedcare.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UploadRateLimiter {
    private final ConcurrentHashMap<Long, Bucket> buckets = new ConcurrentHashMap<>();
    public void check(Long userId) { check(userId, System.currentTimeMillis() / 60000); }
    void check(Long userId, long minute) {
        if (buckets.size() > 5000) buckets.entrySet().removeIf(e -> e.getValue().minute < minute);
        buckets.compute(userId, (key, old) -> {
            if (old == null || old.minute != minute) return new Bucket(minute, 1);
            if (old.count >= 20) throw new BusinessException(429, "图片上传过于频繁，请稍后再试");
            return new Bucket(minute, old.count + 1);
        });
    }
    private record Bucket(long minute, int count) {}
}
