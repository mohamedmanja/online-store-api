package com.harmony.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDto {
    @NotBlank private String currentPassword;
    @NotBlank @Size(min = 8) private String newPassword;
}
