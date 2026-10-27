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
@TableName("staff_booking")
public class StaffBooking implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long staffId;
    private Long orderId;
    private Long dispatchId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // HELD, CONFIRMED, RELEASED, FINISHED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
