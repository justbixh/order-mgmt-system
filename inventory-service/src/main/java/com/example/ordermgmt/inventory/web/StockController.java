package com.example.ordermgmt.inventory.web;

import com.example.ordermgmt.inventory.domain.StockItem;
import com.example.ordermgmt.inventory.domain.StockItemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

/** Read-only view of stock plus a restock operation, mainly to make the demo easy to follow. */
@RestController
@RequestMapping("/api/inventory/products")
@Validated
@Tag(name = "Inventory")
public class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);

    private final StockItemRepository stock;

    public StockController(StockItemRepository stock) {
        this.stock = stock;
    }

    /** Stock level as returned by the API. */
    public record StockView(String productId, String name, int availableQuantity, int reservedQuantity) {
        static StockView from(StockItem s) {
            return new StockView(s.getProductId(), s.getName(), s.getAvailableQuantity(), s.getReservedQuantity());
        }
    }

    @GetMapping
    @Operation(summary = "List all products with their stock levels")
    public List<StockView> list() {
        return stock.findAll().stream().map(StockView::from).toList();
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get the stock level of one product")
    public StockView get(@PathVariable String productId) {
        return stock.findById(productId).map(StockView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product " + productId));
    }

    @PostMapping("/{productId}/restock")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    @Operation(summary = "Add units to the available stock")
    public StockView restock(@PathVariable String productId, @RequestParam @Min(1) int quantity) {
        // Pessimistic lock so a concurrent reservation cannot interleave with the update.
        StockItem item = stock.findAllForUpdate(List.of(productId)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product " + productId));
        item.restock(quantity);
        log.info("Restocked {} by {}", productId, quantity);
        return StockView.from(item);
    }
}
