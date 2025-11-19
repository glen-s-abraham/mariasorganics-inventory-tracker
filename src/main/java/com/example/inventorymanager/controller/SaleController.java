package com.example.inventorymanager.controller;

import com.example.inventorymanager.model.Inventory;
import com.example.inventorymanager.model.Product;
import com.example.inventorymanager.model.Sale;
import com.example.inventorymanager.service.InventoryService;
import com.example.inventorymanager.service.ProductService;
import com.example.inventorymanager.service.SaleService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/sales")
public class SaleController {

    private final SaleService saleService;
    private final ProductService productService;
    private final InventoryService inventoryService;

    public SaleController(SaleService saleService, ProductService productService, InventoryService inventoryService) {
        this.saleService = saleService;
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public String listSales(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "saleDate") String sortField,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {
        int pageSize = 10;
        Page<Sale> salesPage = saleService.getAllSales(page, pageSize, sortField, sortDir);

        model.addAttribute("sales", salesPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", salesPage.getTotalPages());
        model.addAttribute("totalItems", salesPage.getTotalElements());
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

        return "sales";
    }

    @GetMapping("/new")
    public String showNewSaleForm(Model model) {
        Sale sale = new Sale();
        sale.setSaleDate(LocalDate.now()); // Default to today

        model.addAttribute("sale", sale);
        model.addAttribute("products", productService.getAllProducts());
        model.addAttribute("batches", List.of()); // Empty initially, populated via JS

        return "sale_form";
    }

    @PostMapping
    public String createSale(@ModelAttribute Sale sale,
            @RequestParam Long productId,
            @RequestParam Long inventoryId,
            RedirectAttributes redirectAttributes) {
        try {
            // Set the product and inventory
            Product product = productService.getProductById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            Inventory inventory = inventoryService.getInventoryById(inventoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Inventory batch not found"));

            sale.setProduct(product);
            sale.setInventory(inventory);

            saleService.createSale(sale);
            redirectAttributes.addFlashAttribute("message", "Sale created successfully!");
            return "redirect:/sales";
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/sales/new";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Sale sale = saleService.getSaleById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));

        model.addAttribute("sale", sale);
        model.addAttribute("products", productService.getAllProducts());
        // FIX: Use getBatchesForEdit to include current batch even if 0 qty
        model.addAttribute("batches",
                saleService.getBatchesForEdit(sale.getProduct().getId(), sale.getInventory().getId()));

        return "sale_form";
    }

    @PostMapping("/{id}")
    public String updateSale(@PathVariable Long id,
            @ModelAttribute Sale sale,
            @RequestParam Long productId,
            @RequestParam Long inventoryId,
            RedirectAttributes redirectAttributes) {
        try {
            // Set the product and inventory
            Product product = productService.getProductById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            Inventory inventory = inventoryService.getInventoryById(inventoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Inventory batch not found"));

            sale.setProduct(product);
            sale.setInventory(inventory);

            saleService.updateSale(id, sale);
            redirectAttributes.addFlashAttribute("message", "Sale updated successfully!");
            return "redirect:/sales";
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/sales/" + id + "/edit";
        }
    }

    @GetMapping("/{id}/delete")
    public String deleteSale(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            saleService.deleteSale(id);
            redirectAttributes.addFlashAttribute("message", "Sale deleted successfully! Inventory restored.");
            return "redirect:/sales";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/sales";
        }
    }
}
