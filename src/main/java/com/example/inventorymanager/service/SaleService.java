package com.example.inventorymanager.service;

import com.example.inventorymanager.model.Inventory;
import com.example.inventorymanager.model.Sale;
import com.example.inventorymanager.repository.SaleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final InventoryService inventoryService;

    public SaleService(SaleRepository saleRepository, InventoryService inventoryService) {
        this.saleRepository = saleRepository;
        this.inventoryService = inventoryService;
    }

    public Page<Sale> getAllSales(int pageNo, int pageSize, String sortField, String sortDir) {
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir != null ? sortDir : "desc"),
                sortField != null ? sortField : "saleDate");
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);
        return saleRepository.findAll(pageable);
    }

    public Page<Sale> getSalesByProduct(Long productId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, Sort.by("saleDate").descending());
        return saleRepository.findByProductId(productId, pageable);
    }

    public Optional<Sale> getSaleById(Long id) {
        return saleRepository.findById(id);
    }

    /**
     * Create a new sale and deduct from inventory
     * MANDATORY: Prevents selling from expired batches
     */
    @Transactional
    public Sale createSale(Sale sale) {
        // Get the inventory batch
        Inventory inventory = inventoryService.getInventoryById(sale.getInventory().getId())
                .orElseThrow(() -> new IllegalArgumentException("Inventory batch not found"));

        // MANDATORY: Prevent selling from expired batches
        if (inventory.getExpiryDate() != null && inventory.getExpiryDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Cannot sell from expired batch. Batch: " +
                    inventory.getBatchCode() + ", Expired on: " + inventory.getExpiryDate());
        }

        // Validate quantity availability
        int available = inventoryService.getAvailableQuantity(sale.getInventory().getId());
        if (sale.getQuantity() > available) {
            throw new IllegalStateException("Insufficient inventory. Available: " + available +
                    ", Requested: " + sale.getQuantity());
        }

        // Deduct quantity from inventory
        inventoryService.updateQuantity(sale.getInventory().getId(), -sale.getQuantity());

        // Validate before saving
        sale.validate();

        // Save the sale
        return saleRepository.save(sale);
    }

    /**
     * Update an existing sale
     * FIX: Properly handles quantity validation to account for freed quantity from
     * current sale
     * MANDATORY: Prevents selling from expired batches
     */
    @Transactional
    public Sale updateSale(Long id, Sale updatedSale) {
        Optional<Sale> existingOpt = saleRepository.findById(id);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Sale not found with id: " + id);
        }

        Sale existingSale = existingOpt.get();

        // If inventory batch changed or quantity changed
        boolean inventoryChanged = !existingSale.getInventory().getId().equals(updatedSale.getInventory().getId());
        boolean quantityChanged = !existingSale.getQuantity().equals(updatedSale.getQuantity());

        if (inventoryChanged || quantityChanged) {
            // Get the new inventory batch
            Inventory newInventory = inventoryService.getInventoryById(updatedSale.getInventory().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Inventory batch not found"));

            // MANDATORY: Prevent selling from expired batches
            if (newInventory.getExpiryDate() != null && newInventory.getExpiryDate().isBefore(LocalDate.now())) {
                throw new IllegalStateException("Cannot sell from expired batch. Batch: " +
                        newInventory.getBatchCode() + ", Expired on: " + newInventory.getExpiryDate());
            }

            // FIX: Calculate effective available quantity
            // If same batch: current available + quantity from this sale
            // If different batch: just current available
            int currentAvailable = inventoryService.getAvailableQuantity(updatedSale.getInventory().getId());
            int effectiveAvailable = inventoryChanged ? currentAvailable
                    : currentAvailable + existingSale.getQuantity();

            // Validate new quantity against effective available
            if (updatedSale.getQuantity() > effectiveAvailable) {
                throw new IllegalStateException("Insufficient inventory. Effective available: " + effectiveAvailable +
                        " (current: " + currentAvailable + ", from this sale: " +
                        (inventoryChanged ? 0 : existingSale.getQuantity()) + "), Requested: "
                        + updatedSale.getQuantity());
            }

            // Restore old quantity and deduct new quantity
            if (inventoryChanged) {
                // Different batches: restore to old, deduct from new
                inventoryService.updateQuantity(existingSale.getInventory().getId(), existingSale.getQuantity());
                inventoryService.updateQuantity(updatedSale.getInventory().getId(), -updatedSale.getQuantity());
            } else {
                // Same batch: adjust by the difference
                int quantityDifference = updatedSale.getQuantity() - existingSale.getQuantity();
                inventoryService.updateQuantity(existingSale.getInventory().getId(), -quantityDifference);
            }
        }

        // Update sale details
        existingSale.setProduct(updatedSale.getProduct());
        existingSale.setInventory(updatedSale.getInventory());
        existingSale.setQuantity(updatedSale.getQuantity());
        existingSale.setSellingPrice(updatedSale.getSellingPrice());
        existingSale.setSaleDate(updatedSale.getSaleDate());

        // Validate before saving
        existingSale.validate();

        return saleRepository.save(existingSale);
    }

    /**
     * Delete a sale and restore quantity to inventory
     */
    @Transactional
    public void deleteSale(Long id) {
        Optional<Sale> saleOpt = saleRepository.findById(id);
        if (saleOpt.isEmpty()) {
            throw new IllegalArgumentException("Sale not found with id: " + id);
        }

        Sale sale = saleOpt.get();

        // Restore quantity to inventory
        inventoryService.updateQuantity(sale.getInventory().getId(), sale.getQuantity());

        // Delete the sale
        saleRepository.deleteById(id);
    }

    /**
     * Get available batches for a product with quantity > 0
     * Excludes expired batches
     */
    public List<Inventory> getAvailableBatches(Long productId) {
        return inventoryService.getAvailableBatches(productId).stream()
                .filter(inv -> inv.getExpiryDate() == null || inv.getExpiryDate().isAfter(LocalDate.now()))
                .collect(Collectors.toList());
    }

    /**
     * FIX: Get batches for edit mode - includes current batch even if 0 quantity
     * This ensures the edit form can display the currently selected batch
     */
    public List<Inventory> getBatchesForEdit(Long productId, Long currentInventoryId) {
        List<Inventory> availableBatches = new ArrayList<>(getAvailableBatches(productId));

        // If current batch is not in the list (because it has 0 qty), add it
        boolean currentBatchIncluded = availableBatches.stream()
                .anyMatch(b -> b.getId().equals(currentInventoryId));

        if (!currentBatchIncluded && currentInventoryId != null) {
            inventoryService.getInventoryById(currentInventoryId).ifPresent(currentBatch -> {
                // Only add if it belongs to the same product and not expired
                if (currentBatch.getProduct().getId().equals(productId) &&
                        (currentBatch.getExpiryDate() == null
                                || currentBatch.getExpiryDate().isAfter(LocalDate.now()))) {
                    availableBatches.add(0, currentBatch); // Add at beginning
                }
            });
        }

        return availableBatches;
    }
}
