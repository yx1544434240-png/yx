package com.agedcare.modules.order.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderCreateDTO {
    @NotNull(message = "服务对象老人ID不能为空")
    @Positive private Long elderId;

    /**
     * 预约类型: SELF-老人本人自约, FAMILY-家属代约 (为空时后端自动推断)
     */
    @Pattern(regexp="SELF|FAMILY") private String requesterType;

    @NotNull(message = "服务项目ID不能为空")
    @Positive private Long serviceItemId;

    @NotNull(message = "预约开始时间不能为空")
    @Future(message = "预约时间必须为未来时间")
    private LocalDateTime plannedStartAt;

    @NotBlank(message = "服务地址不能为空")
    @Size(max=255) private String address;

    @DecimalMin("-180") @DecimalMax("180") private BigDecimal longitude;
    @DecimalMin("-90") @DecimalMax("90") private BigDecimal latitude;
    @Pattern(regexp="GCJ02|WGS84") private String coordinateType;

    @NotBlank(message = "联系人姓名不能为空")
    @Size(max=64) private String contactName;

    @NotBlank(message = "联系人电话不能为空")
    @Pattern(regexp="[0-9+() -]{6,20}") private String contactPhone;

    @Size(max=1000) private String remark;

    /**
     * 客户端防重复提交幂等键(如UUID)
     */
    @Size(max=64) private String idempotencyKey;
    @AssertTrue(message="经纬度必须同时填写或同时留空")
    public boolean isCoordinatesPaired() { return (longitude == null) == (latitude == null); }
}
