package com.harmony.store.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UserPreferencesDto {
    private UUID defaultShippingAddressId;
    private UUID defaultBillingAddressId;
}
