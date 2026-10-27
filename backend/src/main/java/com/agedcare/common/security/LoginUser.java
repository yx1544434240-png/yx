package com.agedcare.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String role;
    private String openid;
    private String avatar;
}
