package com.harmony.store.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateShipmentDto {

    // Set from path variable, not request body
    private UUID orderId;

    @NotBlank private String shippoShipmentId;
    @NotBlank private String shippoRateId;
    @NotBlank private String carrier;
    @NotBlank private String serviceCode;
    @NotBlank private String serviceName;

    @NotNull @DecimalMin("0.1") private double weightOz;
    @NotNull @DecimalMin("0.1") private double lengthCm;
    @NotNull @DecimalMin("0.1") private double widthCm;
    @NotNull @DecimalMin("0.1") private double heightCm;

    @Pattern(regexp = "PDF|PNG|ZPLII")
    private String labelFormat = "PDF";
}
