package com.agedcare;
import com.agedcare.common.security.*;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.fulfillment.entity.ServiceRecord;
import com.agedcare.modules.fulfillment.mapper.ServiceRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class PrivateMediaFlowIntegrationTest extends IsolatedIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JwtTokenProvider jwt; @Autowired ObjectMapper json;
    @Autowired ServiceOrderMapper orders; @Autowired DispatchAttemptMapper attempts; @Autowired ServiceRecordMapper records;
    String token(long id,String role){return jwt.generateToken(LoginUser.builder().id(id).role(role).build());}
    @Test void realUploadDownloadAndRecordSubmissionEnforceOrderPrivacy() throws Exception {
        var order=ServiceOrder.builder().orderNo("PRIVATE_"+System.nanoTime()).requesterUserId(5L).elderId(201L).serviceItemId(301L)
                .serviceNameSnapshot("照片访问测试").priceSnapshot(BigDecimal.TEN).durationMinutesSnapshot(30)
                .plannedStartAt(LocalDateTime.now()).plannedEndAt(LocalDateTime.now().plusHours(1))
                .addressSnapshot("隔离测试地址").contactNameSnapshot("测试用户").contactPhoneSnapshot("13800000000")
                .status("IN_SERVICE").dispatchRound(1).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        orders.insert(order);
        var attempt=DispatchAttempt.builder().orderId(order.getId()).staffId(401L).dispatchRound(1).attemptNo(1).dispatchType("AUTO")
                .status("ACCEPTED").deadline(LocalDateTime.now().plusMinutes(2)).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        attempts.insert(attempt); order.setCurrentDispatchId(attempt.getId());orders.updateById(order);
        records.insert(ServiceRecord.builder().orderId(order.getId()).staffId(401L).actualStartAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        byte[] bytes;try(var stream=new ClassPathResource("static/service-images/meal.jpg").getInputStream()){bytes=stream.readAllBytes();}
        String staff=token(2L,"STAFF"),resident=token(5L,"RESIDENT"),other=token(7L,"RESIDENT");
        var upload=mvc.perform(multipart("/upload/image").file(new MockMultipartFile("file","photo.jpg","image/jpeg",bytes))
                .param("type","fulfillment").param("orderId",order.getId().toString()).header("Authorization","Bearer "+staff))
                .andExpect(jsonPath("$.code").value(200)).andReturn();
        String path=json.readTree(upload.getResponse().getContentAsString()).path("data").path("url").asText();
        assertTrue(path.startsWith("/media/private/fulfillment/"));
        mvc.perform(get(path)).andExpect(jsonPath("$.code").value(401)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(path).header("Authorization","Bearer "+resident)).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get(path).header("Authorization","Bearer "+staff)).andExpect(content().bytes(bytes));
        String body=json.writeValueAsString(java.util.Map.of("orderId",order.getId().toString(),"serviceSummary","服务已经完成","attachmentsJson",json.writeValueAsString(java.util.List.of(path))));
        mvc.perform(post("/fulfillment/submit-record").contentType("application/json").content(body).header("Authorization","Bearer "+staff)).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get(path).header("Authorization","Bearer "+resident)).andExpect(content().bytes(bytes)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(path).header("Authorization","Bearer "+other)).andExpect(jsonPath("$.code").value(403));
    }
}
