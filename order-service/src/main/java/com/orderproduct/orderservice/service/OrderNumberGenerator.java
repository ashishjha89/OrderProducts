package com.orderproduct.orderservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class OrderNumberGenerator {

    public String getUniqueOrderNumber() {
        return UUID.randomUUID().toString();
    }

}
