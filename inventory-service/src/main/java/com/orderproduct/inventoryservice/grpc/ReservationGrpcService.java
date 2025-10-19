package com.orderproduct.inventoryservice.grpc;

import java.util.List;
import java.util.stream.Collectors;

import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import com.orderproduct.inventoryservice.common.exception.ErrorComponent;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotEnoughItemException;
import com.orderproduct.inventoryservice.common.exception.OrderReservationNotAllowedException;
import com.orderproduct.inventoryservice.dto.request.ItemReservationRequest;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.service.ReservationManagementService;

import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@Slf4j
@AllArgsConstructor
public class ReservationGrpcService extends ReservationServiceGrpc.ReservationServiceImplBase {

    private final ReservationManagementService reservationManagementService;

    @Override
    public void reserveProducts(ReserveProductsRequest request,
            StreamObserver<ReserveProductsResponse> responseObserver) {

        log.info("gRPC:ReserveProducts - Reserving products for order: {}", request.getOrderNumber());

        try {
            OrderReservationRequest orderRequest = convertToOrderReservationRequest(request);

            List<AvailableInventoryResponse> inventoryResponses = reservationManagementService
                    .reserveProductsIfAvailable(orderRequest);

            ReserveProductsResponse grpcResponse = convertToGrpcResponse(inventoryResponses);

            log.info("gRPC:ReserveProducts - Successfully reserved products for order: {} with {} items",
                    request.getOrderNumber(), grpcResponse.getAvailableInventoryCount());

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
        } catch (NotEnoughItemException e) {
            log.warn("gRPC:ReserveProducts - Insufficient stock for order: {}", request.getOrderNumber());
            responseObserver.onError(buildNotEnoughItemError(e));
        } catch (OrderReservationNotAllowedException e) {
            log.warn("gRPC:ReserveProducts - Reservation not allowed for order: {}", request.getOrderNumber());
            responseObserver.onError(buildOrderReservationNotAllowedError(e));
        } catch (InternalServerException e) {
            log.error("gRPC:ReserveProducts - Internal server error for order: {}", request.getOrderNumber(), e);
            responseObserver.onError(buildInternalServerError(e));
        } catch (Exception e) {
            log.error("gRPC:ReserveProducts - Unexpected error for order: {}", request.getOrderNumber(), e);
            responseObserver.onError(buildUnexpectedError(e));
        }
    }

    private OrderReservationRequest convertToOrderReservationRequest(ReserveProductsRequest grpcRequest) {
        List<ItemReservationRequest> itemRequests = grpcRequest.getItemReservationRequestsList().stream()
                .map(grpcItem -> new ItemReservationRequest(grpcItem.getSkuCode(), grpcItem.getQuantity()))
                .collect(Collectors.toList());

        return new OrderReservationRequest(grpcRequest.getOrderNumber(), itemRequests);
    }

    private ReserveProductsResponse convertToGrpcResponse(List<AvailableInventoryResponse> inventoryResponses) {
        ReserveProductsResponse.Builder responseBuilder = ReserveProductsResponse.newBuilder();

        for (AvailableInventoryResponse inventoryResponse : inventoryResponses) {
            responseBuilder.addAvailableInventory(
                    com.orderproduct.inventoryservice.grpc.AvailableInventoryResponse.newBuilder()
                            .setSkuCode(inventoryResponse.skuCode())
                            .setAvailableQuantity(inventoryResponse.availableQuantity())
                            .build());
        }

        return responseBuilder.build();
    }

    private StatusRuntimeException buildNotEnoughItemError(NotEnoughItemException e) {
        ErrorInfo errorInfo = buildBaseErrorInfo(e.getErrorCode());

        // Add unavailable products as metadata
        if (e.getUnavailableProducts() != null && !e.getUnavailableProducts().isEmpty()) {
            String unavailableSkus = e.getUnavailableProducts().stream()
                    .map(item -> item.skuCode())
                    .collect(Collectors.joining(","));
            errorInfo = errorInfo.toBuilder()
                    .putMetadata("unavailable_products", unavailableSkus)
                    .putMetadata("unavailable_count", String.valueOf(e.getUnavailableProducts().size()))
                    .build();
        }

        Status rpcStatus = buildBaseStatus(Code.RESOURCE_EXHAUSTED, e.getErrorMessage())
                .addDetails(Any.pack(errorInfo))
                .build();
        return StatusProto.toStatusRuntimeException(rpcStatus);
    }

    private StatusRuntimeException buildOrderReservationNotAllowedError(OrderReservationNotAllowedException e) {
        ErrorInfo errorInfo = buildBaseErrorInfo(e.getErrorCode());
        Status rpcStatus = buildBaseStatus(Code.FAILED_PRECONDITION, e.getErrorMessage())
                .addDetails(Any.pack(errorInfo))
                .build();
        return StatusProto.toStatusRuntimeException(rpcStatus);
    }

    private StatusRuntimeException buildInternalServerError(InternalServerException e) {
        ErrorInfo errorInfo = buildBaseErrorInfo(e.getErrorCode());
        Status rpcStatus = buildBaseStatus(Code.INTERNAL, e.getErrorMessage())
                .addDetails(Any.pack(errorInfo))
                .build();

        return StatusProto.toStatusRuntimeException(rpcStatus);
    }

    private StatusRuntimeException buildUnexpectedError(Exception e) {
        ErrorInfo errorInfo = buildBaseErrorInfo(
                ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE)
                .toBuilder()
                .putMetadata("original_exception", e.getClass().getSimpleName())
                .build();

        Status rpcStatus = buildBaseStatus(Code.INTERNAL, ErrorComponent.somethingWentWrongMsg)
                .addDetails(Any.pack(errorInfo))
                .build();

        return StatusProto.toStatusRuntimeException(rpcStatus);
    }

    private ErrorInfo buildBaseErrorInfo(String errorCode) {
        return ErrorInfo.newBuilder().setReason(errorCode).build();
    }

    private Status.Builder buildBaseStatus(Code code, String message) {
        return Status.newBuilder().setCode(code.getNumber()).setMessage(message);
    }

}