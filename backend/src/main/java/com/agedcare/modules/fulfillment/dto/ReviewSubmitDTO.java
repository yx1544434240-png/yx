package com.agedcare.modules.fulfillment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ReviewSubmitDTO {
    @NotNull @Positive
    private Long orderId;
    @NotNull @Min(1) @Max(5)
    private Integer rating;
    @Size(max=200)
    private String content;
    @Size(max=255)
    private String tags;
    private boolean followUp;
    @Size(max=32)
    private String issueType;
}
