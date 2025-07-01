package com.orderproduct.inventoryservice.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_reservation", indexes = {
                @Index(name = "idx_sku_code_status", columnList = "skuCode,status"),
                @Index(name = "idx_order_sku", columnList = "orderNumber,skuCode")
}, uniqueConstraints = {
                @UniqueConstraint(columnNames = { "orderNumber", "skuCode" })
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Reservation {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "order_number", nullable = false)
        private String orderNumber;

        @Column(name = "sku_code", nullable = false)
        private String skuCode;

        @Column(name = "reserved_quantity", nullable = false, columnDefinition = "INT CHECK (reserved_quantity >= 0)")
        private int reservedQuantity;

        @Column(name = "reserved_at", nullable = false)
        private LocalDateTime reservedAt;

        @Column(name = "status", nullable = false)
        @Enumerated(EnumType.STRING)
        private ReservationState status;

        @Override
        public boolean equals(Object o) {
                if (this == o)
                        return true;
                if (o == null || getClass() != o.getClass())
                        return false;
                Reservation reservation = (Reservation) o;
                return reservedQuantity == reservation.reservedQuantity && skuCode.equals(reservation.skuCode)
                                && orderNumber.equals(reservation.orderNumber) && status == reservation.status;
        }

        @Override
        public int hashCode() {
                return Objects.hash(skuCode, orderNumber, reservedQuantity, status);
        }

}

                                
                                
                                
                                