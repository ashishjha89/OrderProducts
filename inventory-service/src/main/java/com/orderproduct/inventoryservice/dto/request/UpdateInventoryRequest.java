package com.orderproduct.inventoryservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateInventoryRequest(
                @NotBlank(message = "SKU code cannot be blank.") @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "SKU code can only contain alphanumeric characters, hyphens, and underscores.") @Size(max = 100, message = "SKU code length must be less than 100 characters.") String skuCode,
                @Min(value = 0, message = "Quantity must be non-negative.") int quantity) {
}