package com.orderproduct.inventoryservice.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidKafkaEventException extends ApiException {

    public InvalidKafkaEventException(String message) {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_KAFKA_EVENT_ERROR",
                message);
    }

}
