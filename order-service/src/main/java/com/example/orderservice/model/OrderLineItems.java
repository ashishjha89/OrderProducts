package com.example.orderservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "order_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLineItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String skuCode;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderLineItems that = (OrderLineItems) o;
        return Objects.equals(id, that.id) && skuCode.equals(that.skuCode) && price.equals(that.price) && quantity.equals(that.quantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, skuCode, price, quantity);
    }
}
