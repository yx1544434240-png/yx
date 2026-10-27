package com.agedcare.common.enums;

import lombok.Getter;

@Getter
public enum DispatchStatus {
    PENDING("PENDING", "等待接单"),
    ACCEPTED("ACCEPTED", "已接单"),
    REJECTED("REJECTED", "已拒单"),
    EXPIRED("EXPIRED", "已超时"),
    REVOKED("REVOKED", "已撤销");

    private final String code;
    private final String desc;

    DispatchStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
