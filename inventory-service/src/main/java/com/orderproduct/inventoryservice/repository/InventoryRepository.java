package com.orderproduct.inventoryservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orderproduct.inventoryservice.entity.Inventory;

import lombok.NonNull;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findBySkuCode(String skuCode);

    @NonNull
    List<Inventory> findBySkuCodeIn(List<String> skuCodes);

    @Modifying
    @Query("DELETE FROM Inventory i WHERE i.skuCode = :skuCode")
    int deleteBySkuCode(@Param("skuCode") String skuCode);

    @Modifying
    @Query("UPDATE Inventory i SET i.onHandQuantity = :quantity WHERE i.skuCode = :skuCode")
    int updateQuantityBySkuCode(@Param("skuCode") String skuCode, @Param("quantity") int quantity);
}
