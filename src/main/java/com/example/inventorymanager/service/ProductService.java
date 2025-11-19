package com.example.inventorymanager.service;

import com.example.inventorymanager.model.Product;
import com.example.inventorymanager.repository.InventoryRepository;
import com.example.inventorymanager.repository.ProductRepository;
import com.example.inventorymanager.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SaleRepository saleRepository;

    @Autowired
    public ProductService(ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            SaleRepository saleRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.saleRepository = saleRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Page<Product> findPaginated(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        return productRepository.findAll(pageable);
    }

    public Page<Product> findPaginated(int pageNo, int pageSize, String keyword) {
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        if (keyword != null && !keyword.isEmpty()) {
            return productRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(keyword, keyword,
                    pageable);
        }
        return productRepository.findAll(pageable);
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        // Check if product has sales - we DO NOT want to auto-delete sales
        long salesCount = saleRepository.countByProductId(id);
        if (salesCount > 0) {
            throw new IllegalStateException("Cannot delete product with existing sales records. " +
                    "This product has " + salesCount + " sale(s) associated with it.");
        }

        // Auto-remove inventory batches (orphan removal)
        inventoryRepository.deleteByProductId(id);

        // Delete the product
        productRepository.deleteById(id);
    }
}
