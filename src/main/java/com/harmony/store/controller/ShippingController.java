package com.harmony.store.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmony.store.config.UserPrincipal;
import com.harmony.store.model.Order;
import com.harmony.store.repository.OrderRepository;
import com.harmony.store.dto.CreateShipmentDto;
import com.harmony.store.dto.PackageSpecDto;
import com.harmony.store.dto.RateQuote;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.harmony.store.model.Shipment;
import com.harmony.store.service.ShippingService;

@Slf4j
@RestController
@RequestMapping("/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;
    private final OrderRepository orderRepo;
    private final ObjectMapper objectMapper;

    @Value("${app.shippo.webhook-secret:}") private String webhookSecret;

    // ── Admin — rate quotes ───────────────────────────────────────────────────

    @PostMapping("/orders/{orderId}/rates")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public List<RateQuote> getRates(@PathVariable UUID orderId,
                                     @Valid @RequestBody PackageSpecDto dto) {
        return shippingService.getRates(orderId, dto);
    }

    // ── Admin — create shipment / label ───────────────────────────────────────

    @PostMapping("/orders/{orderId}/shipments")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Shipment createShipment(@PathVariable UUID orderId,
                                    @Valid @RequestBody CreateShipmentDto dto) {
        return shippingService.createShipment(orderId, dto);
    }

    // ── Admin — list shipments for an order ──────────────────────────────────

    @GetMapping("/orders/{orderId}/shipments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Shipment> findByOrder(@PathVariable UUID orderId) {
        return shippingService.findByOrder(orderId);
    }

    // ── Admin — manual tracking refresh ──────────────────────────────────────

    @PostMapping("/shipments/{id}/track")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public Shipment refreshTracking(@PathVariable UUID id) {
        return shippingService.refreshTracking(id);
    }

    // ── Customer — own orders only ────────────────────────────────────────────

    @GetMapping("/my-orders/{orderId}/shipments")
    public List<Shipment> findMyOrderShipments(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Order order = orderRepo.findById(orderId)
                .filter(o -> o.getUser() != null
                        && o.getUser().getId().toString().equals(principal.getId()))
                .orElse(null);
        if (order == null) return List.of();
        return shippingService.findByOrder(orderId);
    }

    // ── Shippo webhook ────────────────────────────────────────────────────────

    @PostMapping("/webhooks/shippo")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Boolean> shippoWebhook(HttpServletRequest request) throws IOException {
        byte[] rawBody = request.getInputStream().readAllBytes();
        String signature = request.getHeader("shippo-webhook-signature");

        if (webhookSecret != null && !webhookSecret.isBlank() && signature != null) {
            String expected = hmacSha256(webhookSecret, rawBody);
            if (!expected.equals(signature)) {
                log.warn("Shippo webhook signature mismatch — ignoring");
                return Map.of("ignored", true);
            }
        }

        JsonNode payload = objectMapper.readTree(rawBody);
        shippingService.handleWebhook(payload);
        return Map.of("received", true);
    }

    private String hmacSha256(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}
