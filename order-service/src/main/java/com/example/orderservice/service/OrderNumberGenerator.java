package com.example.orderservice.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderNumberGenerator {

    public String getUniqueOrderNumber() {
        return UUID.randomUUID().toString();
    }

}
