package com.agedcare.common.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    WAIT_DISPATCH("WAIT_DISPATCH", "待派单"),
    WAIT_ACCEPT("WAIT_ACCEPT", "待接单"),
    WAIT_VISIT("WAIT_VISIT", "待上门"),
    IN_SERVICE("IN_SERVICE", "服务中"),
    WAIT_CONFIRM("WAIT_CONFIRM", "待确认"),
    COMPLETED("COMPLETED", "已完成"),
    WAIT_MANUAL("WAIT_MANUAL", "待人工处理"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    OrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean canCancel() {
        return this == WAIT_DISPATCH || this == WAIT_ACCEPT || this == WAIT_VISIT || this == WAIT_MANUAL;
    }
}
