package com.agedcare.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_status_log")
public class OrderStatusLog implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorRole;
    private String reason;
    private LocalDateTime createdAt;
}
