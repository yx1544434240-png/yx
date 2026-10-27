package com.agedcare.common.enums;

import lombok.Getter;

@Getter
public enum UserRole {
    ADMIN("ADMIN", "管理员"),
    STAFF("STAFF", "服务人员"),
    RESIDENT("RESIDENT", "社区居民");

    private final String code;
    private final String desc;

    UserRole(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
