package com.agedcare.modules.fulfillment.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class ServiceRecordDTO {
    @NotNull @Positive private Long orderId;
    @NotBlank @Size(max=4000) private String serviceSummary;
    @Size(max=4096) private String attachmentsJson;
}
