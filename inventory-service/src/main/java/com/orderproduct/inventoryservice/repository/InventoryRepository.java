package com.orderproduct.inventoryservice.repository;

import com.orderproduct.inventoryservice.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findBySkuCode(String skuCode);

    List<Inventory> findBySkuCodeIn(List<String> skuCodes);

    @Modifying
    @Query("DELETE FROM Inventory i WHERE i.skuCode = :skuCode")
    int deleteBySkuCode(@Param("skuCode") String skuCode);
}
