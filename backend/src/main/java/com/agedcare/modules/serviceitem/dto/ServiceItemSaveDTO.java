package com.agedcare.modules.serviceitem.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class ServiceItemSaveDTO {
    @Positive private Long id;
    @NotBlank @Size(max=64) private String name;
    @NotNull @Pattern(regexp="MEAL|CLEANING|ERRAND|ESCORT") private String category;
    @Size(max=4000) private String description;
    @NotNull @DecimalMin("0") @Digits(integer=8,fraction=2) private BigDecimal price;
    @NotNull @Min(1) @Max(600) private Integer durationMinutes;
    @Size(max=255) private String requiredSkills;
    @Size(max=128) private String serviceArea;
    @Pattern(regexp="ON_SHELF|OFF_SHELF") private String status;
    @Size(max=255) @Pattern(regexp="|[a-zA-Z0-9_-]+|/media/service-images/[a-zA-Z0-9-]+\\.(jpg|jpeg|png)") private String icon;
    @Min(0) @Max(99999) private Integer sortOrder;
}
