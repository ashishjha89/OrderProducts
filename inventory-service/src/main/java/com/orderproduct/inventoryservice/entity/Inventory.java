package com.orderproduct.inventoryservice.entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // This results in table creation with unique index on skuCode column
    @Column(name = "sku_code", nullable = false, unique = true)
    private String skuCode;

    @Column(name = "on_hand_quantity", nullable = false, columnDefinition = "INT CHECK (on_hand_quantity >= 0)")
    private int onHandQuantity;

    public static Inventory createInventory(String skuCode, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        return Inventory.builder()
                .skuCode(skuCode)
                .onHandQuantity(quantity)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Inventory inventory = (Inventory) o;
        return onHandQuantity == inventory.onHandQuantity && skuCode.equals(inventory.skuCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skuCode, onHandQuantity);
    }
}
