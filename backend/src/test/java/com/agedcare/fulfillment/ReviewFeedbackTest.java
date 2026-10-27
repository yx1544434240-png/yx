package com.agedcare.fulfillment;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.fulfillment.dto.ReviewSubmitDTO;
import com.agedcare.modules.fulfillment.entity.ServiceComplaint;
import com.agedcare.modules.fulfillment.entity.ServiceReview;
import com.agedcare.modules.fulfillment.mapper.ServiceComplaintMapper;
import com.agedcare.modules.fulfillment.mapper.ServiceReviewMapper;
import com.agedcare.modules.fulfillment.service.FulfillmentService;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.validation.Validation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReviewFeedbackTest {
    @Mock ServiceOrderMapper orders;
    @Mock ServiceReviewMapper reviews;
    @Mock ServiceComplaintMapper complaints;
    @InjectMocks FulfillmentService service;
    @BeforeAll static void mappings() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "review-test");
        TableInfoHelper.initTableInfo(assistant, ServiceReview.class);
        TableInfoHelper.initTableInfo(assistant, ServiceComplaint.class);
    }
    @BeforeEach void login() { UserContext.setUser(LoginUser.builder().id(5L).role("RESIDENT").build()); }
    @AfterEach void logout() { UserContext.clear(); }
    void completedOrder() { when(orders.selectById(10L)).thenReturn(ServiceOrder.builder().id(10L).requesterUserId(5L).elderId(101L).status("COMPLETED").build()); }
    ServiceReview review(int rating) { return ServiceReview.builder().orderId(10L).rating(rating).tags("上门迟到").content("  希望准时上门  ").build(); }
    @Test void lowScoreWithoutConsentDoesNotCreateComplaint() {
        completedOrder();
        service.submitReview(review(1), false, null);
        verify(reviews).insert(any(ServiceReview.class));
        verifyNoInteractions(complaints);
    }
    @Test void consentingLowScoreCreatesOwnerLinkedPendingFeedback() {
        completedOrder();
        var review = review(2);
        service.submitReview(review, true, "上门迟到");
        var capture = ArgumentCaptor.forClass(ServiceComplaint.class);
        verify(complaints).insert(capture.capture());
        assertEquals(10L, capture.getValue().getOrderId());
        assertEquals(5L, capture.getValue().getComplainantId());
        assertEquals("PENDING", capture.getValue().getStatus());
        assertEquals("[评价跟进] 上门迟到", capture.getValue().getComplaintTitle());
        assertEquals("希望准时上门", review.getContent());
    }
    @Test void invalidFollowUpOrTagNeverWritesReviewOrComplaint() {
        completedOrder();
        assertThrows(BusinessException.class, () -> service.submitReview(review(5), true, "上门迟到"));
        assertThrows(BusinessException.class, () -> service.submitReview(review(2), true, null));
        var invalid = review(2); invalid.setTags("非法标签");
        assertThrows(BusinessException.class, () -> service.submitReview(invalid, false, null));
        verify(reviews, never()).insert(any(ServiceReview.class));
        verifyNoInteractions(complaints);
    }
    @Test void completedReviewCannotCreateAnotherFollowUp() {
        completedOrder();
        when(reviews.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.submitReview(review(2), true, "上门迟到"));
        verify(reviews, never()).insert(any(ServiceReview.class));
        verifyNoInteractions(complaints);
    }
    @Test void adminStateTransitionsRejectStaleActions() {
        UserContext.setUser(LoginUser.builder().id(1L).role("ADMIN").build());
        when(complaints.update(isNull(), any())).thenReturn(1, 0);
        service.startComplaint(88L);
        assertThrows(BusinessException.class, () -> service.startComplaint(88L));
    }
    @Test void resolvedFeedbackCannotBeOverwritten() {
        when(complaints.selectById(88L)).thenReturn(ServiceComplaint.builder().id(88L).status("RESOLVED").build());
        assertThrows(BusinessException.class, () -> service.handleComplaint(88L, "重复回复"));
        verify(complaints, never()).update(isNull(), any());
    }
    @Test void requestValidationRequiresRatingAndLimitsContentTo200Characters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var dto = new ReviewSubmitDTO(); dto.setOrderId(10L);
            assertFalse(factory.getValidator().validate(dto).isEmpty());
            dto.setRating(5); dto.setContent("字".repeat(201));
            assertFalse(factory.getValidator().validate(dto).isEmpty());
            dto.setContent("可选意见");
            assertTrue(factory.getValidator().validate(dto).isEmpty());
        }
    }
}
