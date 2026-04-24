package com.harmony.store.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PackageSpecDto {

    @NotNull @DecimalMin("0.1")
    private double weightOz;

    @NotNull @DecimalMin("0.1")
    private double lengthCm;

    @NotNull @DecimalMin("0.1")
    private double widthCm;

    @NotNull @DecimalMin("0.1")
    private double heightCm;
}
