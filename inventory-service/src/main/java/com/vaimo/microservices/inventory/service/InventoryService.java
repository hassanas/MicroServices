package com.vaimo.microservices.inventory.service;

import com.vaimo.microservices.inventory.dto.InventoryResponse;
import com.vaimo.microservices.inventory.model.Inventory;
import com.vaimo.microservices.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    /**
     * Check if a product has sufficient quantity in stock
     * @param productId the product ID to check
     * @param requiredQuantity the quantity needed
     * @return true if product is in stock with required quantity, false otherwise
     */
    public boolean isInStock(String productId, Integer requiredQuantity) {
        log.info("Checking if product {} is in stock with required quantity {}", productId, requiredQuantity);

        Optional<Inventory> inventory = inventoryRepository.findByProductId(productId);

        if (inventory.isEmpty()) {
            log.warn("Product {} not found in inventory", productId);
            return false;
        }

        boolean inStock = inventory.get().getQuantity() >= requiredQuantity;
        log.info("Product {} inventory check result: {}", productId, inStock);

        return inStock;
    }

    /**
     * Get inventory by product ID
     */
    public Optional<InventoryResponse> getInventoryByProductId(String productId) {
        log.info("Retrieving inventory for product {}", productId);

        return inventoryRepository.findByProductId(productId)
                .map(this::toResponse);
    }

    /**
     * Save or update inventory
     */
    public InventoryResponse saveInventory(String productId, Integer quantity) {
        log.info("Saving inventory for product {} with quantity {}", productId, quantity);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElse(Inventory.builder()
                        .productId(productId)
                        .build());

        inventory.setQuantity(quantity);
        Inventory saved = inventoryRepository.save(inventory);

        log.info("Inventory saved successfully for product {}", productId);
        return toResponse(saved);
    }

    /**
     * Convert Inventory entity to InventoryResponse DTO
     */
    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt()
        );
    }
}

