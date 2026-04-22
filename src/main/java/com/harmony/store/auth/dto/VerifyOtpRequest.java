package com.harmony.store.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank private String pendingToken;
    @NotBlank private String code;
    @NotBlank private String method;
}
