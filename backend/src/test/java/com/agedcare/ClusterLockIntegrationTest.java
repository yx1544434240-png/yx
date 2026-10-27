package com.agedcare;
import com.agedcare.common.config.DispatchClusterLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class ClusterLockIntegrationTest extends IsolatedIntegrationTest {
    @Autowired DispatchClusterLock lock;
    @Test void onlyOneConnectionCanScanAndFailureReleasesLock() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var executor=Executors.newSingleThreadExecutor();
        try {
            var first=executor.submit(() -> lock.run(() -> {
                entered.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertFalse(lock.run(() -> fail("concurrent scan must not run")));
            release.countDown(); assertTrue(first.get(5,TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,()->lock.run(()->{throw new IllegalStateException("simulated failure");}));
            assertTrue(lock.run(()->{}));
        } finally { release.countDown();executor.shutdownNow(); }
    }
}
