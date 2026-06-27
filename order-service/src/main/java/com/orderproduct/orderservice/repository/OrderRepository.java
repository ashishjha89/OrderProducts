package com.orderproduct.orderservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orderproduct.orderservice.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT o FROM Order o JOIN FETCH o.orderLineItemsList WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithLineItems(@Param("orderNumber") String orderNumber);
}
