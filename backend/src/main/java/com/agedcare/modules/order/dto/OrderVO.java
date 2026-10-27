package com.agedcare.modules.order.dto;

import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.fulfillment.entity.ServiceRecord;
import com.agedcare.modules.fulfillment.entity.ServiceReview;
import com.agedcare.modules.fulfillment.entity.ServiceComplaint;
import com.agedcare.modules.order.entity.OrderStatusLog;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.staff.dto.StaffDetailVO;
import lombok.Data;

import java.util.List;

@Data
public class OrderVO {
    private ServiceOrder order;
    private String requesterType; // SELF, FAMILY
    private String requesterTypeDesc; // 老人自约, 家属代约
    private String requesterName; // 下单人真实姓名或用户名
    private Long payerUserId; // 已支付时为实际支付人，未支付时为下单人
    private String payerName;
    private boolean paid;
    private boolean canPay; // 仅详情接口按当前用户计算：老人本人或已审核绑定家属、未支付且未取消
    private String settlementNote;
    private OrderElderVO elder; // 精简后的老人信息，不含 idCard
    private DispatchAttempt currentDispatch;
    private StaffDetailVO currentStaff;
    private ServiceRecord serviceRecord;
    private ServiceReview serviceReview;
    private List<ServiceComplaint> feedbackRecords;
    private List<OrderStatusLog> logs;
    private OrderTrackingVO tracking;
}
