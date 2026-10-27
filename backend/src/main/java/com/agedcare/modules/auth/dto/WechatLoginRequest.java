package com.agedcare.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatLoginRequest {
    @NotBlank(message = "微信授权Code不能为空")
    private String code;
    private String nickName;
    private String avatarUrl;
    private String role; // 注册/首次进入时指定的角色(如 RESIDENT 或 STAFF)
}
