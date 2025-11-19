package com.example.inventorymanager.repository;

import com.example.inventorymanager.model.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {
    Page<Sale> findByProductId(Long productId, Pageable pageable);

    Page<Sale> findByInventoryId(Long inventoryId, Pageable pageable);

    long countByProductId(Long productId);

    long countByInventoryId(Long inventoryId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Sale s SET s.deleted = true WHERE s.inventory.id = :inventoryId")
    void deleteByInventoryId(Long inventoryId);
}
