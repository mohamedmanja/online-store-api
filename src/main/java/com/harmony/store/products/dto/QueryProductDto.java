package com.harmony.store.products.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class QueryProductDto {

    private String search;

    private String categorySlug;

    @Min(1)
    private int page = 1;

    @Min(1) @Max(100)
    private int limit = 20;
}
