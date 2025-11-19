package com.example.inventorymanager.repository;

import com.example.inventorymanager.model.Inventory;
import com.example.inventorymanager.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Page<Inventory> findByProductId(Long productId, Pageable pageable);

    Optional<Inventory> findTopByProductOrderByBatchSequenceDesc(Product product);
}
