package com.agedcare.common.security;
import org.junit.jupiter.api.Test;
import com.agedcare.common.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
class UploadRateLimiterTest {
    @Test void limitsPerUserAndResetsNextMinute() {
        var limiter = new UploadRateLimiter();
        for(int i=0;i<20;i++) limiter.check(1L,100);
        assertEquals(429, assertThrows(BusinessException.class, () -> limiter.check(1L,100)).getCode());
        assertDoesNotThrow(() -> limiter.check(2L,100));
        assertDoesNotThrow(() -> limiter.check(1L,101));
    }
}
