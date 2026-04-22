package com.harmony.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmony.store.dto.CreateOrderFromSessionDto;
import com.harmony.store.service.OrdersService;
import com.harmony.store.dto.CheckoutItemDto;
import com.harmony.store.dto.CreateCheckoutDto;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.harmony.store.model.Order;

@Slf4j
@Service
public class PaymentsService {

    private static final RequestOptions STRIPE_REQUEST_OPTIONS;

    static {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder();
        RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride(builder, "2026-03-25.dahlia");
        STRIPE_REQUEST_OPTIONS = builder.build();
    }

    private final OrdersService ordersService;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;
    private final String frontendUrl;

    public PaymentsService(OrdersService ordersService,
                            ObjectMapper objectMapper,
                            @Value("${app.stripe.secret-key}") String stripeSecretKey,
                            @Value("${app.stripe.webhook-secret:}") String webhookSecret,
                            @Value("${app.frontend-url}") String frontendUrl) {
        this.ordersService  = ordersService;
        this.objectMapper   = objectMapper;
        this.webhookSecret  = webhookSecret;
        this.frontendUrl    = frontendUrl;
        Stripe.apiKey = stripeSecretKey;
    }

    // ── Create Checkout Session ───────────────────────────────────────────────

    public Map<String, String> createCheckoutSession(CreateCheckoutDto dto, String userId) {
        List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();

        for (CheckoutItemDto item : dto.getItems()) {
            SessionCreateParams.LineItem.PriceData.ProductData productData =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(item.getName())
                            .build();

            // Build price data
            SessionCreateParams.LineItem.PriceData.Builder priceDataBuilder =
                    SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("usd")
                            .setProductData(productData)
                            .setUnitAmount(Math.round(item.getPrice() * 100)); // cents

            lineItems.add(SessionCreateParams.LineItem.builder()
                    .setPriceData(priceDataBuilder.build())
                    .setQuantity((long) item.getQuantity())
                    .build());
        }

        // Serialize items for metadata (only productId, price, quantity to stay under 500 chars)
        String itemsMeta;
        String addressMeta;
        try {
            List<Map<String, Object>> slim = dto.getItems().stream()
                    .map(i -> Map.<String, Object>of(
                            "productId", i.getProductId(),
                            "price", i.getPrice(),
                            "quantity", i.getQuantity()))
                    .toList();
            itemsMeta = objectMapper.writeValueAsString(slim);
            addressMeta = dto.getShippingAddress() != null
                    ? objectMapper.writeValueAsString(dto.getShippingAddress()) : null;
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize metadata");
        }

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/checkout/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/cart")
                .putMetadata("userId", userId)
                .putMetadata("items", itemsMeta)
                .addAllLineItem(lineItems);

        if (addressMeta != null) params.putMetadata("shippingAddress", addressMeta);

        try {
            Session session = Session.create(params.build(), STRIPE_REQUEST_OPTIONS);
            if (session.getUrl() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create Stripe session");
            }
            return Map.of("sessionUrl", session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe session creation failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment session creation failed");
        }
    }

    // ── Webhook Handler ───────────────────────────────────────────────────────

    public void handleWebhook(byte[] rawBody, String signature) {
        log.info("Webhook received — body size: {} bytes, signature present: {}",
                rawBody.length, signature != null && !signature.isBlank());

        Event event;
        try {
            event = Webhook.constructEvent(new String(rawBody), signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Webhook signature verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
        }

        log.info("Webhook event — id: {}, type: {}, apiVersion: {}",
                event.getId(), event.getType(), event.getApiVersion());

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                log.info("Inside checkout.session.completed switch case — event apiVersion: {}", event.getApiVersion());

                var deserializer = event.getDataObjectDeserializer();
                String sessionId;
                Long amountTotal;
                Map<String, String> metadata;

                if (deserializer.getObject().isPresent()) {
                    Session session = (Session) deserializer.getObject().get();
                    sessionId  = session.getId();
                    amountTotal = session.getAmountTotal();
                    metadata   = session.getMetadata();
                    log.info("checkout.session.completed (SDK) — sessionId: {}, paymentStatus: {}, amountTotal: {}, currency: {}",
                            session.getId(), session.getPaymentStatus(), session.getAmountTotal(), session.getCurrency());
                } else {
                    log.warn("SDK could not deserialize session object (apiVersion: {}), falling back to raw JSON parsing",
                            event.getApiVersion());
                    try {
                        JsonNode node = objectMapper.readTree(deserializer.getRawJson());
                        sessionId   = node.path("id").asText(null);
                        amountTotal = node.path("amount_total").isNull() ? null : node.path("amount_total").asLong();
                        Map<String, String> meta = new HashMap<>();
                        node.path("metadata").fields().forEachRemaining(e -> meta.put(e.getKey(), e.getValue().asText()));
                        metadata = meta;
                        log.info("checkout.session.completed (raw JSON) — sessionId: {}, paymentStatus: {}, amountTotal: {}, currency: {}",
                                sessionId, node.path("payment_status").asText(), amountTotal, node.path("currency").asText());
                    } catch (JsonProcessingException e) {
                        log.error("Failed to parse raw Stripe event JSON: {}", e.getMessage(), e);
                        return;
                    }
                }

                log.info("Session metadata: {}", metadata);
                onCheckoutComplete(sessionId, amountTotal, metadata);
            }
            case "checkout.session.expired" ->
                    log.warn("Stripe session expired: {}", event.getId());
            default ->
                    log.debug("Unhandled Stripe event: {}", event.getType());
        }
    }

    // ── Private: fulfil the order ─────────────────────────────────────────────

    private void onCheckoutComplete(String sessionId, Long amountTotal, Map<String, String> meta) {
        if (meta == null) {
            log.error("onCheckoutComplete — metadata is null for session {}", sessionId);
            return;
        }

        String userId   = meta.get("userId");
        String rawItems = meta.get("items");
        String rawAddr  = meta.get("shippingAddress");

        log.info("onCheckoutComplete — sessionId: {}, userId: {}, rawItems: {}, rawAddr: {}",
                sessionId, userId, rawItems, rawAddr);

        if (userId == null || rawItems == null) {
            log.error("Webhook missing metadata for session {} — userId={}, items={}",
                    sessionId, userId, rawItems);
            return;
        }

        try {
            List<CreateOrderFromSessionDto.CheckoutItem> items =
                    objectMapper.readValue(rawItems, new TypeReference<>() {});
            Map<String, String> shippingAddress = rawAddr != null
                    ? objectMapper.readValue(rawAddr, new TypeReference<>() {}) : null;

            log.info("Parsed items: {}, shippingAddress: {}", items, shippingAddress);

            CreateOrderFromSessionDto dto = new CreateOrderFromSessionDto();
            dto.setUserId(userId);
            dto.setStripeSessionId(sessionId);
            dto.setItems(items);
            dto.setTotal((amountTotal != null ? amountTotal : 0L) / 100.0);
            dto.setShippingAddress(shippingAddress);

            log.info("Calling createFromStripeSession — sessionId: {}, userId: {}, total: {}, itemCount: {}",
                    dto.getStripeSessionId(), dto.getUserId(), dto.getTotal(), dto.getItems().size());

            try {
                ordersService.createFromStripeSession(dto);
                log.info("Order created successfully for session {}", sessionId);
            } catch (OrdersService.DuplicateSessionException e) {
                log.info("Duplicate webhook — order already exists for session {}", sessionId);
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook metadata for session {}: {}", sessionId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error processing checkout.session.completed for session {}: {}",
                    sessionId, e.getMessage(), e);
            throw e;
        }
    }
}
