package com.example.inventorymanager.repository;

import com.example.inventorymanager.model.Inventory;
import com.example.inventorymanager.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long>, JpaSpecificationExecutor<Inventory> {
    Page<Inventory> findByProductId(Long productId, Pageable pageable);

    Optional<Inventory> findTopByProductOrderByBatchSequenceDesc(Product product);

    long countByProductId(Long productId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Inventory i SET i.deleted = true WHERE i.product.id = :productId")
    void deleteByProductId(Long productId);
}
