package com.agedcare.modules.auth.dto;

import com.agedcare.common.security.LoginUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private LoginUser userInfo;
    private boolean newUser;
}
