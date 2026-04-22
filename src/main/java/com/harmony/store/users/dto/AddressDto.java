package com.harmony.store.users.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddressDto {
    private String name;
    @NotBlank private String line1;
    private String line2;
    @NotBlank private String city;
    @NotBlank private String state;
    @NotBlank private String postalCode;
    @NotBlank private String country;
}
