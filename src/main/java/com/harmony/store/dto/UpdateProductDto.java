package com.harmony.store.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateProductDto {

    @Size(min = 2)
    private String name;

    private String description;

    @DecimalMin("0.00")
    private BigDecimal price;

    @Min(0)
    private Integer stock;

    private UUID categoryId;

    private Boolean isActive;
}
