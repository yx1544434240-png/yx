package com.agedcare.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class OrderNoGenerator {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "ORD" + timestamp + randomPart;
    }
}
