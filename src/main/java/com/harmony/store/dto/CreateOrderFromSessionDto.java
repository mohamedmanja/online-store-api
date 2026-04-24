package com.harmony.store.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateOrderFromSessionDto {

    private String userId;
    private String stripeSessionId;
    private List<CheckoutItem> items;
    private double total;
    private Map<String, String> shippingAddress;

    @Data
    public static class CheckoutItem {
        private String productId;
        private double price;
        private int quantity;
    }
}
