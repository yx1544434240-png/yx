package com.agedcare.modules.fulfillment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.*;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value={"id", "complainantId", "status", "handleUserId", "handleResult", "handledAt", "createdAt", "updatedAt"}, allowGetters=true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("service_complaint")
public class ServiceComplaint implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long complainantId;
    @NotBlank @Size(max=128)
    private String complaintTitle;
    @NotBlank @Size(max=4000)
    private String complaintContent;
    private String status; // PENDING, PROCESSING, RESOLVED, REJECTED
    private Long handleUserId;
    private String handleResult;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
