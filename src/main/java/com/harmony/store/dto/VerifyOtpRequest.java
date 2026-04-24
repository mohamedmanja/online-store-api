package com.harmony.store.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank private String pendingToken;
    @NotBlank private String code;
    @NotBlank private String method;
}
