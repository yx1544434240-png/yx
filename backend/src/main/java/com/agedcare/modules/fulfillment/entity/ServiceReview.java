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

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value={"id", "elderId", "reviewerUserId", "createdAt"}, allowGetters=true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("service_review")
public class ServiceReview implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @NotNull @Positive
    private Long orderId;
    private Long elderId;
    private Long reviewerUserId;
    @NotNull @Min(1) @Max(5)
    private Integer rating; // 1~5
    @Size(max=200)
    private String content;
    @Size(max=255)
    private String tags;
    private LocalDateTime createdAt;
}
