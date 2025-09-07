package com.orderproduct.inventoryservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;

import lombok.NonNull;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @NonNull
    List<Reservation> findBySkuCodeInAndStatus(List<String> skuCodes, ReservationState status);

    @NonNull
    List<Reservation> findByOrderNumber(String orderNumber);

    @NonNull
    List<Reservation> findByOrderNumberAndSkuCodeIn(String orderNumber, List<String> skuCodes);

    void deleteByOrderNumberAndSkuCodeIn(String orderNumber, List<String> skuCodes);

}