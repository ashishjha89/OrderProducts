package com.orderproduct.orderservice.controller;

import lombok.NonNull;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

import com.orderproduct.orderservice.common.ApiException;
import com.orderproduct.orderservice.common.BadRequestException;
import com.orderproduct.orderservice.common.ErrorComponent;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import java.util.Map;

@Component
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, @NonNull DataFetchingEnvironment env) {
        switch (ex) {
            case BadRequestException ignored -> {
                return GraphqlErrorBuilder.newError(env)
                        .errorType(ErrorType.ValidationError)
                        .message(ErrorComponent.badRequestMsg)
                        .extensions(Map.of("code", "BAD_USER_INPUT"))
                        .build();
            }
            case InventoryNotInStockException ignored -> {
                return GraphqlErrorBuilder.newError(env)
                        .errorType(ErrorType.ValidationError)
                        .message(ex.getMessage())
                        .extensions(Map.of("code", ErrorComponent.INVENTORY_NOT_IN_STOCK_ERROR_CODE))
                        .build();
            }
            case InternalServerException ignored -> {
                return GraphqlErrorBuilder.newError(env)
                        .errorType(ErrorType.DataFetchingException)
                        .message(ErrorComponent.somethingWentWrongMsg)
                        .extensions(Map.of("code", "INTERNAL_SERVER_ERROR"))
                        .build();
            }
            case ApiException apiEx -> {
                boolean isClientError = apiEx.getHttpStatus().is4xxClientError();
                return GraphqlErrorBuilder.newError(env)
                        .errorType(isClientError ? ErrorType.ValidationError : ErrorType.DataFetchingException)
                        .message(apiEx.getMessage())
                        .extensions(java.util.Map.of("code", apiEx.getErrorCode()))
                        .build();
            }
            default -> {
            }
        }
        return null;
    }
}
