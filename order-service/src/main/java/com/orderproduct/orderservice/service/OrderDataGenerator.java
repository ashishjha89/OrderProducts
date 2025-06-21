package com.orderproduct.orderservice.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class OrderDataGenerator {

    public String getUniqueOrderNumber() {
        return generateUniqueId();
    }

    public String getUniqueOutboxEventId() {
        return generateUniqueId();
    }

    public Long getCurrentTimestamp() {
        return Instant.now().toEpochMilli();
    }

    private String generateUniqueId() {
        return UUID.randomUUID().toString();
    }

}
