package com.orderproduct.inventoryservice.common.util;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeProvider {

    public LocalDateTime getCurrentTimestamp() {
        return LocalDateTime.now();
    }
}