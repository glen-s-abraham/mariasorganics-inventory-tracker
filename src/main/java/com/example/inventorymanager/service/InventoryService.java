package com.example.inventorymanager.service;

import com.example.inventorymanager.model.Inventory;
import com.example.inventorymanager.model.Product;
import com.example.inventorymanager.repository.InventoryRepository;
import com.example.inventorymanager.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.criteria.Predicate;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final SaleRepository saleRepository;

    @Autowired
    public InventoryService(InventoryRepository inventoryRepository, SaleRepository saleRepository) {
        this.inventoryRepository = inventoryRepository;
        this.saleRepository = saleRepository;
    }

    public Page<Inventory> getInventoryByProduct(Long productId, int pageNo, int pageSize,
            String keyword, LocalDate startDate, LocalDate endDate,
            String status, String sortField, String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir != null ? sortDir : "asc"),
                sortField != null ? sortField : "batchCode");

        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);

        Specification<Inventory> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by Product ID
            predicates.add(criteriaBuilder.equal(root.get("product").get("id"), productId));

            // Filter by Keyword (Batch Code)
            if (StringUtils.hasText(keyword)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("batchCode")),
                        "%" + keyword.toLowerCase() + "%"));
            }

            // Filter by Date Range (Entry Date)
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("entryDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("entryDate"), endDate));
            }

            // Filter by Status
            if ("ACTIVE".equalsIgnoreCase(status)) {
                // Expiry date is null OR expiry date is in future
                Predicate expiryNull = criteriaBuilder.isNull(root.get("expiryDate"));
                Predicate expiryFuture = criteriaBuilder.greaterThan(root.get("expiryDate"), LocalDate.now());
                predicates.add(criteriaBuilder.or(expiryNull, expiryFuture));
            } else if ("EXPIRED".equalsIgnoreCase(status)) {
                // Expiry date is NOT null AND expiry date is in past or today
                Predicate expiryNotNull = criteriaBuilder.isNotNull(root.get("expiryDate"));
                Predicate expiryPast = criteriaBuilder.lessThanOrEqualTo(root.get("expiryDate"), LocalDate.now());
                predicates.add(criteriaBuilder.and(expiryNotNull, expiryPast));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return inventoryRepository.findAll(spec, pageable);
    }

    public Optional<Inventory> getInventoryById(Long id) {
        return inventoryRepository.findById(id);
    }

    public Inventory saveInventory(Inventory inventory) {
        if (inventory.getId() == null) {
            // New inventory entry, generate batch code
            Product product = inventory.getProduct();
            Optional<Inventory> lastInventory = inventoryRepository.findTopByProductOrderByBatchSequenceDesc(product);

            long nextSequence = lastInventory.map(inv -> inv.getBatchSequence() + 1).orElse(1L);
            inventory.setBatchSequence(nextSequence);
            inventory.setBatchCode(product.getSku() + "-" + nextSequence);
        }
        return inventoryRepository.save(inventory);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteInventory(Long id) {
        // Auto-remove associated sales (orphan removal)
        saleRepository.deleteByInventoryId(id);

        inventoryRepository.deleteById(id);
    }

    /**
     * Update inventory quantity (used by sales)
     * 
     * @param inventoryId    The inventory batch ID
     * @param quantityChange The change in quantity (negative for sales, positive
     *                       for reversals)
     */
    public void updateQuantity(Long inventoryId, int quantityChange) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findById(inventoryId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            int newQuantity = inventory.getQuantity() + quantityChange;
            if (newQuantity < 0) {
                throw new IllegalStateException("Inventory quantity cannot be negative. Batch: " +
                        inventory.getBatchCode() + ", Current: " + inventory.getQuantity() +
                        ", Change: " + quantityChange);
            }
            inventory.setQuantity(newQuantity);
            inventoryRepository.save(inventory);
        } else {
            throw new IllegalArgumentException("Inventory not found with id: " + inventoryId);
        }
    }

    /**
     * Get available quantity for a batch
     */
    public int getAvailableQuantity(Long inventoryId) {
        return inventoryRepository.findById(inventoryId)
                .map(Inventory::getQuantity)
                .orElse(0);
    }

    /**
     * Get all batches with available quantity for a product
     */
    public List<Inventory> getAvailableBatches(Long productId) {
        Specification<Inventory> spec = (root, query, criteriaBuilder) -> {
            Predicate productMatch = criteriaBuilder.equal(root.get("product").get("id"), productId);
            Predicate quantityGreaterThanZero = criteriaBuilder.greaterThan(root.get("quantity"), 0);
            return criteriaBuilder.and(productMatch, quantityGreaterThanZero);
        };
        return inventoryRepository.findAll(spec);
    }
}
