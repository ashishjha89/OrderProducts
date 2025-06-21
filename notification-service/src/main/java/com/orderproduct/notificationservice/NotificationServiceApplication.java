package com.orderproduct.notificationservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    @KafkaListener(topics = "outbox.event.Order")
    public void handleNotification(String orderPlacedEvent) {
        log.info("Got message <{}>", orderPlacedEvent);
        // Do some action, e.g. send out an email notification
    }
} 