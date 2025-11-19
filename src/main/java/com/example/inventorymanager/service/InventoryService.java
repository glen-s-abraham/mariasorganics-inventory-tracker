package com.example.inventorymanager.service;

import com.example.inventorymanager.model.Inventory;
import com.example.inventorymanager.model.Product;
import com.example.inventorymanager.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Autowired
    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public Page<Inventory> getInventoryByProduct(Long productId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        return inventoryRepository.findByProductId(productId, pageable);
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

    public void deleteInventory(Long id) {
        inventoryRepository.deleteById(id);
    }
}
