package com.agedcare.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户安全展示脱敏 VO，绝不包含密码及 OpenID 等敏感凭据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryVO implements Serializable {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String avatar;
    private String role;
}
