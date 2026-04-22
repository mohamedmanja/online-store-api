package com.harmony.store.dto;

import lombok.Data;

@Data
public class ResendOtpRequest {
    private String pendingToken;
    private String method;
}
