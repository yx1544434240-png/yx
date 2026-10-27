package com.agedcare.modules.dispatch.entity;

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
@TableName("dispatch_attempt")
public class DispatchAttempt implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Integer dispatchRound;
    private Integer attemptNo;
    private Long staffId;
    private String dispatchType; // AUTO, MANUAL
    private LocalDateTime deadline;
    private String status; // PENDING, ACCEPTED, REJECTED, EXPIRED, REVOKED
    private LocalDateTime acceptedAt;
    private LocalDateTime rejectedAt;
    private String rejectReason;
    private LocalDateTime expiredAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
