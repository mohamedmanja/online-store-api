package com.harmony.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;
import com.harmony.store.dto.CheckoutItemDto;

@Data
public class CreateCheckoutDto {

    @NotEmpty @Valid
    private List<CheckoutItemDto> items;

    private Map<String, String> shippingAddress;
}
