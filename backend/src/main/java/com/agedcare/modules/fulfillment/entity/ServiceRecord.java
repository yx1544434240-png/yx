package com.agedcare.modules.fulfillment.entity;

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
@TableName("service_record")
public class ServiceRecord implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long staffId;
    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    private String serviceSummary;
    private String attachmentsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
