package com.harmony.store.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RateQuote {
    private String carrier;
    private String serviceCode;
    private String serviceName;
    private double rateUsd;
    private Integer estimatedDays;
    private String shippoShipmentId;
    private String shippoRateId;
}
