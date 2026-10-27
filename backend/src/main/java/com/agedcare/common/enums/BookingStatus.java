package com.agedcare.common.enums;

import lombok.Getter;

@Getter
public enum BookingStatus {
    HELD("HELD", "待接单暂扣"),
    CONFIRMED("CONFIRMED", "已接单锁定"),
    RELEASED("RELEASED", "已释放"),
    FINISHED("FINISHED", "已履约完毕");

    private final String code;
    private final String desc;

    BookingStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
