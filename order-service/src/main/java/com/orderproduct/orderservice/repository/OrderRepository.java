package com.orderproduct.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orderproduct.orderservice.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
