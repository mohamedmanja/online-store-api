package com.harmony.store.auth.dto;

import lombok.Data;

@Data
public class ResendOtpRequest {
    private String pendingToken;
    private String method;
}
