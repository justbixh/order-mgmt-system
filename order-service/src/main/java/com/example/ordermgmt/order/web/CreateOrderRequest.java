package com.example.ordermgmt.order.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request body of {@code POST /api/orders}.
 *
 * <p>Simplification: unit prices come from the client. A real system would look prices up in a
 * product catalogue owned by another service.
 */
public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotBlank @Email String customerEmail,
        @NotEmpty List<@Valid Item> items,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code") String currency,
        @Schema(description = "Mock payment token. tok_success (default), tok_declined, tok_insufficient_funds, "
                + "tok_gateway_error (gateway outage: retries then dead-letter)", example = "tok_success")
        String paymentMethod) {

    /** One requested line. */
    public record Item(
            @NotBlank @Schema(example = "SKU-LAPTOP") String productId,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.01") BigDecimal unitPrice) {
    }
}
