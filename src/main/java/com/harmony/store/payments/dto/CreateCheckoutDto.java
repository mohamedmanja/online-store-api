package com.harmony.store.payments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateCheckoutDto {

    @NotEmpty @Valid
    private List<CheckoutItemDto> items;

    private Map<String, String> shippingAddress;
}
