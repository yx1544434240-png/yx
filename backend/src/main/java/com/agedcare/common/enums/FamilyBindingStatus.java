package com.agedcare.common.enums;

import lombok.Getter;

@Getter
public enum FamilyBindingStatus {
    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    REVOKED("REVOKED", "已撤销");

    private final String code;
    private final String desc;

    FamilyBindingStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
