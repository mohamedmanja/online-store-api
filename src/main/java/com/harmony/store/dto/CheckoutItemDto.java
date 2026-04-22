package com.harmony.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutItemDto {

    @NotBlank
    private String productId;

    @NotBlank
    private String name;

    @NotNull @Min(0)
    private double price;

    @NotNull @Min(1)
    private int quantity;

    private String imageUrl;
}
