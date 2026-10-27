package com.agedcare.dispatch;

import com.agedcare.modules.dispatch.mapper.*;
import com.agedcare.modules.dispatch.service.*;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.entity.OrderStatusLog;
import com.agedcare.modules.order.mapper.*;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ExpiredDispatchTransactionTest {
    @Test void expiredOrderCommitsManualStatusAndLogWithoutDispatchAndIsIdempotent() {
        var orders = mock(ServiceOrderMapper.class);
        var logs = mock(OrderStatusLogMapper.class);
        var attempts = mock(DispatchAttemptMapper.class);
        var scores = mock(DispatchScoreMapper.class);
        var bookings = mock(StaffBookingMapper.class);
        var engine = mock(DispatchScoreEngine.class);
        var order = ServiceOrder.builder().id(1L).status("WAIT_DISPATCH")
                .plannedEndAt(LocalDateTime.now().minusMinutes(1)).build();
        when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        var target = new DispatchService(orders, mock(StaffProfileMapper.class), attempts, scores, bookings, logs, engine);
        var manager = new CountingTransactionManager();
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var service = (DispatchService) proxy.getProxy();
        assertNull(service.dispatchOrder(1L, null, "AUTO"));
        assertEquals("WAIT_MANUAL", order.getStatus());
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
        verify(orders).updateById(order);
        verify(logs).insert(any(OrderStatusLog.class));
        assertNull(service.dispatchOrder(1L, null, "AUTO"));
        verifyNoMoreInteractions(logs);
        verifyNoInteractions(attempts, scores, bookings, engine);
    }

    // 使用真实 Spring 事务拦截器验证提交路径；不连接开发数据库。
    static class CountingTransactionManager extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
