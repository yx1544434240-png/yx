package com.agedcare.modules.staff.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class StaffApplicationDTO {
    @Min(0) private int revision;
    @NotBlank @Size(max=32) private String realName;
    @NotBlank @Pattern(regexp="1[3-9][0-9]{9}", message="请填写有效的11位手机号码") private String phone;
    @NotBlank @Size(max=100) private String serviceArea;
    @NotBlank @Size(max=500) private String introduction;
}
