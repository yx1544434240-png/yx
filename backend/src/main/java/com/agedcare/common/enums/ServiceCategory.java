package com.agedcare.common.enums;

import lombok.Getter;

@Getter
public enum ServiceCategory {
    MEAL("MEAL", "助餐送餐"),
    CLEANING("CLEANING", "居家保洁"),
    ERRAND("ERRAND", "代买代办"),
    ESCORT("ESCORT", "陪同办事");

    private final String code;
    private final String desc;

    ServiceCategory(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
