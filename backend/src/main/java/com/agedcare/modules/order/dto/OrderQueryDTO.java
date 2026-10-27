package com.agedcare.modules.order.dto;

import lombok.Data;

@Data
public class OrderQueryDTO {
    @jakarta.validation.constraints.Min(1) private int pageNum = 1;
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) private int pageSize = 10;
    private String orderNo;
    private String status;
    public void setStatus(String status) {
        String value = status == null ? "" : status.trim();
        this.status = value.isEmpty() || "null".equalsIgnoreCase(value) || "undefined".equalsIgnoreCase(value) ? null : value;
    }
    private Long elderId;
    private Long staffId;
}
