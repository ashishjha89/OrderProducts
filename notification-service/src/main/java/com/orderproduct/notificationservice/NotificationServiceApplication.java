package com.orderproduct.notificationservice;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceApplication {

    @Autowired
    private ObjectMapper objectMapper;

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    @KafkaListener(topics = "outbox.event.Order")
    public void handleNotification(String message) throws Exception {
        Map<String, Object> map = objectMapper.readValue(message, Map.class);
        String payloadJson = (String) map.get("payload");
        OrderPlacedEvent event = objectMapper.readValue(payloadJson, OrderPlacedEvent.class);
        log.info("Got message <{}>", event);
        // Do some action, e.g. send out an email notification
    }
}