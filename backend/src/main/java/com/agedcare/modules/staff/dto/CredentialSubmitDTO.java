package com.agedcare.modules.staff.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class CredentialSubmitDTO {
    @Positive private Long id;
    @Min(0) private int revision;
    @NotBlank @Size(max=32) private String skillCode;
    @NotBlank @Size(max=64) private String skillName;
    @NotBlank @Size(max=64) private String certificateNo;
    @NotNull @Size(min=1, max=3)
    private List<@NotBlank @Size(max=255) String> proofUrls;
}
