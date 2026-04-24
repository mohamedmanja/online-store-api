package com.harmony.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmony.store.service.MailService;
import com.harmony.store.model.Order;
import com.harmony.store.repository.OrderRepository;
import com.harmony.store.dto.CreateShipmentDto;
import com.harmony.store.dto.PackageSpecDto;
import com.harmony.store.dto.RateQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.harmony.store.model.Address;
import com.harmony.store.model.Shipment;
import com.harmony.store.model.ShipmentStatus;
import com.harmony.store.repository.ShipmentRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShipmentRepository repo;
    private final OrderRepository orderRepo;
    private final MailService mail;
    private final ObjectMapper objectMapper;

    @Value("${app.shippo.api-key}") private String apiKey;
    @Value("${app.shippo.webhook-secret:}") private String webhookSecret;
    @Value("${app.origin.name:Harmony Store}") private String originName;
    @Value("${app.origin.address:123 Main St}") private String originAddress;
    @Value("${app.origin.city:New York}") private String originCity;
    @Value("${app.origin.state:NY}") private String originState;
    @Value("${app.origin.zip:10001}") private String originZip;
    @Value("${app.origin.country:US}") private String originCountry;
    @Value("${app.origin.email:}") private String originEmail;
    @Value("${app.origin.phone:}") private String originPhone;

    private static final Map<String, ShipmentStatus> SHIPPO_STATUS_MAP = Map.of(
            "UNKNOWN",     ShipmentStatus.unknown,
            "PRE_TRANSIT", ShipmentStatus.pre_transit,
            "TRANSIT",     ShipmentStatus.in_transit,
            "DELIVERED",   ShipmentStatus.delivered,
            "RETURNED",    ShipmentStatus.returned,
            "FAILURE",     ShipmentStatus.failure
    );

    // ── Shippo HTTP helper ────────────────────────────────────────────────────

    private JsonNode shippo(String method, String path, Object body) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String bodyStr = body != null ? objectMapper.writeValueAsString(body) : null;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.goshippo.com" + path))
                    .header("Authorization", "ShippoToken " + apiKey)
                    .header("Content-Type", "application/json");

            if ("POST".equals(method) && bodyStr != null) {
                builder.POST(HttpRequest.BodyPublishers.ofString(bodyStr));
            } else {
                builder.GET();
            }

            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() >= 400) {
                String msg = json.has("detail") ? json.get("detail").asText()
                        : json.has("message") ? json.get("message").asText()
                        : "Shippo API error " + response.statusCode();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            }
            return json;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Shippo request failed: " + e.getMessage());
        }
    }

    // ── Address helpers ───────────────────────────────────────────────────────

    private Map<String, String> fromAddress() {
        return Map.of(
                "name",    originName,
                "street1", originAddress,
                "city",    originCity,
                "state",   originState,
                "zip",     originZip,
                "country", originCountry,
                "email",   originEmail,
                "phone",   originPhone
        );
    }

    private Map<String, String> toAddress(Map<String, String> addr) {
        Map<String, String> result = new java.util.HashMap<>();
        result.put("name",    addr.getOrDefault("name", ""));
        result.put("street1", addr.getOrDefault("line1", ""));
        result.put("city",    addr.getOrDefault("city", ""));
        result.put("state",   addr.getOrDefault("state", ""));
        result.put("zip",     addr.getOrDefault("postalCode", ""));
        result.put("country", addr.getOrDefault("country", "US"));
        String line2 = addr.get("line2");
        if (line2 != null && !line2.isBlank()) result.put("street2", line2);
        return result;
    }

    // ── Rate quotes ───────────────────────────────────────────────────────────

    public List<RateQuote> getRates(UUID orderId, PackageSpecDto pkg) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order " + orderId + " not found"));
        if (order.getShippingAddress() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no shipping address");
        }

        double lengthIn = pkg.getLengthCm() / 2.54;
        double widthIn  = pkg.getWidthCm()  / 2.54;
        double heightIn = pkg.getHeightCm() / 2.54;

        Map<String, Object> shipmentBody = Map.of(
                "address_to",   toAddress(order.getShippingAddress()),
                "address_from", fromAddress(),
                "parcels", List.of(Map.of(
                        "weight",        pkg.getWeightOz(),
                        "mass_unit",     "oz",
                        "length",        String.format("%.2f", lengthIn),
                        "width",         String.format("%.2f", widthIn),
                        "height",        String.format("%.2f", heightIn),
                        "distance_unit", "in"
                )),
                "async", false
        );

        JsonNode shipment = shippo("POST", "/shipments", shipmentBody);
        String shipmentId = shipment.get("object_id").asText();

        List<RateQuote> quotes = new ArrayList<>();
        JsonNode rates = shipment.get("rates");
        if (rates != null && rates.isArray()) {
            for (JsonNode r : rates) {
                String provider    = r.path("provider").asText();
                String token       = r.path("servicelevel").path("token").asText();
                String name        = r.path("servicelevel").path("name").asText();
                double amount      = Double.parseDouble(r.path("amount").asText("0"));
                JsonNode estDays   = r.path("estimated_days");
                Integer days       = estDays.isNull() || estDays.isMissingNode() ? null : estDays.asInt();

                quotes.add(RateQuote.builder()
                        .carrier(provider)
                        .serviceCode(token)
                        .serviceName(provider + " " + name)
                        .rateUsd(amount)
                        .estimatedDays(days)
                        .shippoShipmentId(shipmentId)
                        .shippoRateId(r.path("object_id").asText())
                        .build());
            }
        }

        quotes.sort((a, b) -> Double.compare(a.getRateUsd(), b.getRateUsd()));
        return quotes;
    }

    // ── Label creation ────────────────────────────────────────────────────────

    @Transactional
    public Shipment createShipment(UUID orderId, CreateShipmentDto dto) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order " + orderId + " not found"));

        // Purchase the rate — Shippo calls this a transaction
        Map<String, Object> txnBody = Map.of(
                "rate",            dto.getShippoRateId(),
                "label_file_type", dto.getLabelFormat() != null ? dto.getLabelFormat() : "PDF",
                "async",           false
        );
        JsonNode txn = shippo("POST", "/transactions", txnBody);

        String status = txn.path("status").asText();
        if (!"SUCCESS".equals(status)) {
            StringBuilder msgs = new StringBuilder();
            JsonNode messages = txn.path("messages");
            if (messages.isArray()) messages.forEach(m -> msgs.append(m.path("text").asText()).append("; "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Shippo label creation failed: " + (msgs.length() > 0 ? msgs : status));
        }

        String trackingNumber = txn.path("tracking_number").isNull() ? null : txn.path("tracking_number").asText();
        String labelUrl       = txn.path("label_url").isNull()       ? null : txn.path("label_url").asText();
        String trackingUrl    = txn.path("tracking_url_provider").isNull() ? null : txn.path("tracking_url_provider").asText();

        // Fetch shipment to get rate amount
        BigDecimal rateAmount = null;
        try {
            JsonNode shipmentNode = shippo("GET", "/shipments/" + dto.getShippoShipmentId(), null);
            JsonNode rateNodes = shipmentNode.path("rates");
            if (rateNodes.isArray()) {
                for (JsonNode r : rateNodes) {
                    if (dto.getShippoRateId().equals(r.path("object_id").asText())) {
                        rateAmount = new BigDecimal(r.path("amount").asText("0"));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch rate amount: {}", e.getMessage());
        }

        Shipment shipment = Shipment.builder()
                .order(order)
                .shippoShipmentId(dto.getShippoShipmentId())
                .carrier(dto.getCarrier())
                .serviceCode(dto.getServiceCode())
                .serviceName(dto.getServiceName())
                .weight(BigDecimal.valueOf(dto.getWeightOz()))
                .length(BigDecimal.valueOf(dto.getLengthCm()))
                .width(BigDecimal.valueOf(dto.getWidthCm()))
                .height(BigDecimal.valueOf(dto.getHeightCm()))
                .trackingNumber(trackingNumber)
                .labelUrl(labelUrl)
                .labelFormat(dto.getLabelFormat() != null ? dto.getLabelFormat() : "PDF")
                .trackingUrl(trackingUrl)
                .rateAmount(rateAmount)
                .trackingStatus(ShipmentStatus.label_created)
                .notificationsSent(0)
                .build();

        Shipment saved = repo.save(shipment);

        if (order.getUser() != null) {
            try {
                mail.sendShippingNotification(order, order.getUser(), saved, "shipped");
                repo.updateNotificationsSent(saved.getId(), 1);
            } catch (Exception e) {
                log.error("Failed to send shipping notification: {}", e.getMessage());
            }
        }

        return saved;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public List<Shipment> findByOrder(UUID orderId) {
        return repo.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    // ── Webhook ───────────────────────────────────────────────────────────────

    @Transactional
    public void handleWebhook(JsonNode payload) {
        String event = payload.path("event").asText();
        if (!"track_updated".equals(event)) return;

        JsonNode data = payload.path("data");
        String trackingNumber = data.path("tracking_number").asText(null);
        if (trackingNumber == null || trackingNumber.isBlank()) return;

        repo.findByTrackingNumberWithOrder(trackingNumber).ifPresent(shipment ->
                applyTrackerUpdate(shipment, data));
    }

    // ── Manual refresh ────────────────────────────────────────────────────────

    @Transactional
    public Shipment refreshTracking(UUID shipmentId) {
        Shipment shipment = repo.findByIdWithOrder(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
        if (shipment.getTrackingNumber() == null) return shipment;

        String carrier = shipment.getCarrier().toLowerCase();
        JsonNode tracker = shippo("GET", "/tracks/" + carrier + "/" + shipment.getTrackingNumber(), null);
        return applyTrackerUpdate(shipment, tracker);
    }

    // ── Apply tracker update ──────────────────────────────────────────────────

    private Shipment applyTrackerUpdate(Shipment shipment, JsonNode tracker) {
        JsonNode statusNode = tracker.path("tracking_status");
        String rawStatus    = statusNode.path("status").asText("UNKNOWN");
        ShipmentStatus newStatus = SHIPPO_STATUS_MAP.getOrDefault(rawStatus, ShipmentStatus.unknown);
        String newDetail    = statusNode.path("status_details").isNull() ? null : statusNode.path("status_details").asText();
        String etaStr       = tracker.path("eta").isNull() ? null : tracker.path("eta").asText(null);
        Instant newEst      = etaStr != null ? Instant.parse(etaStr) : null;
        String newUrl       = tracker.path("tracking_url_provider").isNull() ? null : tracker.path("tracking_url_provider").asText(null);

        boolean changed = newStatus != shipment.getTrackingStatus();
        shipment.setTrackingStatus(newStatus);
        shipment.setTrackingDetail(newDetail);
        shipment.setEstimatedDelivery(newEst);
        shipment.setLastTrackedAt(Instant.now());
        if (newUrl != null && shipment.getTrackingUrl() == null) shipment.setTrackingUrl(newUrl);

        Shipment saved = repo.save(shipment);

        if (changed && shipment.getOrder() != null && shipment.getOrder().getUser() != null) {
            sendStatusNotification(saved);
        }

        return saved;
    }

    private void sendStatusNotification(Shipment shipment) {
        var status = shipment.getTrackingStatus();
        var user   = shipment.getOrder().getUser();
        int sent   = shipment.getNotificationsSent();

        try {
            if (status == ShipmentStatus.out_for_delivery && (sent & 2) == 0) {
                mail.sendShippingNotification(shipment.getOrder(), user, shipment, "out_for_delivery");
                repo.updateNotificationsSent(shipment.getId(), sent | 2);
            } else if (status == ShipmentStatus.delivered && (sent & 4) == 0) {
                mail.sendShippingNotification(shipment.getOrder(), user, shipment, "delivered");
                repo.updateNotificationsSent(shipment.getId(), sent | 4);
            }
        } catch (Exception e) {
            log.error("Failed to send status notification: {}", e.getMessage());
        }
    }
}
