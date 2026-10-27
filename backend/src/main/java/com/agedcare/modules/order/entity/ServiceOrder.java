package com.agedcare.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("service_order")
public class ServiceOrder implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private Long requesterUserId;
    private String requesterType; // SELF, FAMILY
    private Long elderId;
    private Long serviceItemId;
    private String serviceNameSnapshot;
    private String serviceDescriptionSnapshot;
    private String requiredSkillsSnapshot;
    private BigDecimal priceSnapshot;
    private Integer durationMinutesSnapshot;
    private LocalDateTime plannedStartAt;
    private LocalDateTime plannedEndAt;
    private String addressSnapshot;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordinateType;
    private String contactNameSnapshot;
    private String contactPhoneSnapshot;
    private String remark;
    private String status; // WAIT_DISPATCH, WAIT_ACCEPT, WAIT_VISIT, IN_SERVICE, WAIT_CONFIRM, COMPLETED, WAIT_MANUAL, CANCELLED

    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long currentDispatchId;
    private Integer dispatchRound;
    private String idempotencyKey;
    private String requestDigest;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private Long confirmedByUserId;
    private LocalDateTime confirmedAt;
    private Long paidByUserId; // 实际支付人(老人本人或已审核绑定的家属)
    private LocalDateTime paidAt; // 支付时间；非空即已支付，每笔订单只能支付一次

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public com.agedcare.common.enums.OrderStatus getStatusEnum() {
        if (this.status == null) return null;
        try {
            return com.agedcare.common.enums.OrderStatus.valueOf(this.status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
