package com.orderproduct.inventoryservice.common.exception;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class InventoryExceptionHandler {

    public <T> T executeInventoryOperation(
            Supplier<T> operation,
            String operationContext,
            Object... contextParams) throws InternalServerException {

        try {
            return operation.get();
        } catch (DataAccessException e) {
            log.error("DataAccessException when {} - Context: {} - Error: {}",
                    operationContext, formatContext(contextParams), e.getMessage(), e);
            throw new InternalServerException();
        } catch (PersistenceException e) {
            log.error("PersistenceException when {} - Context: {} - Error: {}",
                    operationContext, formatContext(contextParams), e.getMessage(), e);
            throw new InternalServerException();
        } catch (Exception e) {
            log.error("Exception when {} - Context: {} - Error: {}",
                    operationContext, formatContext(contextParams), e.getMessage(), e);
            throw new InternalServerException();
        }
    }

    private String formatContext(Object... contextParams) {
        return Arrays.stream(contextParams)
                .map(Object::toString)
                .collect(Collectors.joining(", "));
    }
}
