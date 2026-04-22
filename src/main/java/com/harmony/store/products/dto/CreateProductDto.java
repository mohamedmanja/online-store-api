package com.harmony.store.products.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateProductDto {

    @NotBlank @Size(min = 2)
    private String name;

    private String description;

    @NotNull @DecimalMin("0.00")
    private BigDecimal price;

    @Min(0)
    private int stock;

    private UUID categoryId;

    private Boolean isActive = true;
}
