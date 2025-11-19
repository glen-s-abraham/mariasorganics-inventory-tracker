package com.example.inventorymanager.controller;

import com.example.inventorymanager.model.Inventory;
import com.example.inventorymanager.model.Product;
import com.example.inventorymanager.service.InventoryService;
import com.example.inventorymanager.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Controller
@RequestMapping("/products/{productId}/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final ProductService productService;

    @Autowired
    public InventoryController(InventoryService inventoryService, ProductService productService) {
        this.inventoryService = inventoryService;
        this.productService = productService;
    }

    @GetMapping
    public String listInventory(@PathVariable("productId") Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false, defaultValue = "batchCode") String sortField,
            @RequestParam(required = false, defaultValue = "asc") String sortDir,
            Model model) {
        Product product = productService.getProductById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product Id:" + productId));

        Page<Inventory> inventoryPage = inventoryService.getInventoryByProduct(productId, page, size,
                keyword, startDate, endDate,
                status, sortField, sortDir);

        model.addAttribute("product", product);
        model.addAttribute("inventoryPage", inventoryPage);
        model.addAttribute("inventoryList", inventoryPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", inventoryPage.getTotalPages());
        model.addAttribute("totalItems", inventoryPage.getTotalElements());

        // Pass filter params back to view
        model.addAttribute("keyword", keyword);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("status", status);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

        return "inventory_list";
    }

    @GetMapping("/new")
    public String showInventoryForm(@PathVariable("productId") Long productId, Model model) {
        Product product = productService.getProductById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product Id:" + productId));

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setEntryDate(LocalDate.now());

        model.addAttribute("product", product);
        model.addAttribute("inventory", inventory);
        return "inventory_form";
    }

    @PostMapping("/save")
    public String saveInventory(@PathVariable("productId") Long productId,
            @ModelAttribute("inventory") Inventory inventory) {
        Product product = productService.getProductById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product Id:" + productId));
        inventory.setProduct(product);

        // Calculate expiry date if days are provided
        if (inventory.getExpiryDays() != null && inventory.getEntryDate() != null) {
            inventory.setExpiryDate(inventory.getEntryDate().plusDays(inventory.getExpiryDays()));
        }

        inventoryService.saveInventory(inventory);
        return "redirect:/products/" + productId + "/inventory";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("productId") Long productId,
            @PathVariable("id") Long id,
            Model model) {
        Product product = productService.getProductById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product Id:" + productId));
        Inventory inventory = inventoryService.getInventoryById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid inventory Id:" + id));

        // Calculate expiry days for display
        if (inventory.getExpiryDate() != null && inventory.getEntryDate() != null) {
            long days = ChronoUnit.DAYS.between(inventory.getEntryDate(), inventory.getExpiryDate());
            inventory.setExpiryDays((int) days);
        }

        model.addAttribute("product", product);
        model.addAttribute("inventory", inventory);
        return "inventory_form";
    }

    @GetMapping("/delete/{id}")
    public String deleteInventory(@PathVariable("productId") Long productId,
            @PathVariable("id") Long id,
            RedirectAttributes redirectAttributes) {
        try {
            inventoryService.deleteInventory(id);
            redirectAttributes.addFlashAttribute("message", "Inventory batch deleted successfully!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "An error occurred while deleting the inventory batch.");
        }
        return "redirect:/products/" + productId + "/inventory";
    }
}
