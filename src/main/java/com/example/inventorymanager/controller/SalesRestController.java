package com.example.inventorymanager.controller;

import com.example.inventorymanager.model.Inventory;
import com.example.inventorymanager.service.SaleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
public class SalesRestController {

    private final SaleService saleService;

    public SalesRestController(SaleService saleService) {
        this.saleService = saleService;
    }

    /**
     * Get available batches for a product (for dropdown population)
     * FIX: Includes current batch in edit mode even if quantity is 0
     * 
     * @param includeInventoryId Optional - the current batch ID when editing (to
     *                           ensure it shows)
     */
    @GetMapping("/batches/by-product/{productId}")
    public List<Map<String, Object>> getAvailableBatches(
            @PathVariable Long productId,
            @RequestParam(required = false) Long includeInventoryId) {

        List<Inventory> batches;
        if (includeInventoryId != null) {
            // Edit mode: include current batch even if 0 qty
            batches = saleService.getBatchesForEdit(productId, includeInventoryId);
        } else {
            // New sale mode: only available batches
            batches = saleService.getAvailableBatches(productId);
        }

        return batches.stream().map(batch -> {
            Map<String, Object> batchInfo = new HashMap<>();
            batchInfo.put("id", batch.getId());
            batchInfo.put("batchCode", batch.getBatchCode());
            batchInfo.put("quantity", batch.getQuantity());
            batchInfo.put("expiryDate", batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : null);
            return batchInfo;
        }).collect(Collectors.toList());
    }
}
