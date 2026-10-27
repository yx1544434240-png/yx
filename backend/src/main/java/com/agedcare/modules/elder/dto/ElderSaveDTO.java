package com.agedcare.modules.elder.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class ElderSaveDTO {
    @Positive private Long id;
    @Positive private Long userId;
    @NotBlank @Size(max=64) private String name;
    @Pattern(regexp="MALE|FEMALE|UNKNOWN") private String gender;
    @Pattern(regexp="|[0-9]{15}|[0-9]{17}[0-9Xx]") private String idCard;
    @Min(0) @Max(130) private Integer age;
    @Size(max=20) @Pattern(regexp="|[0-9+() -]{6,20}") private String phone;
    @NotBlank @Size(max=255) private String address;
    @DecimalMin("-180") @DecimalMax("180") private BigDecimal longitude;
    @DecimalMin("-90") @DecimalMax("90") private BigDecimal latitude;
    @Size(max=64) private String community;
    @Size(max=64) private String emergencyContact;
    @Size(max=20) @Pattern(regexp="|[0-9+() -]{6,20}") private String emergencyPhone;
    @Size(max=2000) private String healthNote;
    @Pattern(regexp="NORMAL|MEDIUM|HIGH") private String careLevel;
    @Min(0) @Max(1) private Integer status;
    @AssertTrue(message="经纬度必须同时填写或同时留空")
    public boolean isCoordinatesPaired() { return (longitude == null) == (latitude == null); }
}
