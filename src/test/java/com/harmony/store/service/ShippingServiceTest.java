package com.harmony.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmony.store.model.*;
import com.harmony.store.repository.OrderRepository;
import com.harmony.store.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock ShipmentRepository repo;
    @Mock OrderRepository    orderRepo;
    @Mock MailService        mail;
    @Spy  ObjectMapper       objectMapper = new ObjectMapper();

    @InjectMocks ShippingService shippingService;

    private User buildUser(UUID id) {
        return User.builder().id(id).email("user@example.com").role(UserRole.customer).build();
    }

    private Order buildOrder(UUID orderId, User user) {
        return Order.builder().id(orderId).user(user).build();
    }

    private Shipment buildShipment(UUID id, Order order, String trackingNumber, ShipmentStatus status) {
        return Shipment.builder()
                .id(id)
                .order(order)
                .carrier("USPS")
                .trackingNumber(trackingNumber)
                .trackingStatus(status)
                .notificationsSent(0)
                .build();
    }

    private JsonNode json(String raw) throws Exception {
        return objectMapper.readTree(raw);
    }

    // ── findByOrder ───────────────────────────────────────────────────────────

    @Test
    void findByOrder_returnsShipmentList() {
        UUID orderId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();
        Shipment shipment = buildShipment(UUID.randomUUID(), buildOrder(orderId, buildUser(userId)), "TRACK123", ShipmentStatus.in_transit);
        when(repo.findByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(List.of(shipment));

        List<Shipment> result = shippingService.findByOrder(orderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(shipment);
    }

    @Test
    void findByOrder_noShipments_returnsEmptyList() {
        UUID orderId = UUID.randomUUID();
        when(repo.findByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(List.of());

        assertThat(shippingService.findByOrder(orderId)).isEmpty();
    }

    // ── handleWebhook ─────────────────────────────────────────────────────────

    @Test
    void handleWebhook_nonTrackUpdatedEvent_doesNothing() throws Exception {
        JsonNode payload = json("""
                {"event": "transaction_created", "data": {}}
                """);

        shippingService.handleWebhook(payload);

        verifyNoInteractions(repo);
    }

    @Test
    void handleWebhook_trackUpdatedWithoutTrackingNumber_doesNothing() throws Exception {
        JsonNode payload = json("""
                {"event": "track_updated", "data": {}}
                """);

        shippingService.handleWebhook(payload);

        verify(repo, never()).findByTrackingNumberWithOrder(any());
    }

    @Test
    void handleWebhook_trackingNumberNotFound_doesNothing() throws Exception {
        JsonNode payload = json("""
                {
                  "event": "track_updated",
                  "data": {
                    "tracking_number": "NOTFOUND",
                    "tracking_status": {"status": "TRANSIT"}
                  }
                }
                """);

        when(repo.findByTrackingNumberWithOrder("NOTFOUND")).thenReturn(Optional.empty());

        shippingService.handleWebhook(payload);

        verify(repo, never()).save(any());
    }

    @Test
    void handleWebhook_validTrackUpdate_updatesShipmentStatus() throws Exception {
        UUID userId    = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        User user      = buildUser(userId);
        Order order    = buildOrder(orderId, user);
        Shipment shipment = buildShipment(shipmentId, order, "TRACK999", ShipmentStatus.in_transit);

        JsonNode payload = json("""
                {
                  "event": "track_updated",
                  "data": {
                    "tracking_number": "TRACK999",
                    "tracking_status": {
                      "status": "DELIVERED",
                      "status_details": "Package delivered"
                    },
                    "eta": null,
                    "tracking_url_provider": null
                  }
                }
                """);

        when(repo.findByTrackingNumberWithOrder("TRACK999")).thenReturn(Optional.of(shipment));
        when(repo.save(shipment)).thenReturn(shipment);

        shippingService.handleWebhook(payload);

        assertThat(shipment.getTrackingStatus()).isEqualTo(ShipmentStatus.delivered);
        assertThat(shipment.getTrackingDetail()).isEqualTo("Package delivered");
        verify(repo).save(shipment);
    }

    @Test
    void handleWebhook_statusUnchanged_doesNotSendNotification() throws Exception {
        UUID userId    = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        User user      = buildUser(userId);
        Order order    = buildOrder(orderId, user);
        // Shipment is already DELIVERED — same status coming in
        Shipment shipment = buildShipment(shipmentId, order, "TRACK888", ShipmentStatus.delivered);

        JsonNode payload = json("""
                {
                  "event": "track_updated",
                  "data": {
                    "tracking_number": "TRACK888",
                    "tracking_status": {"status": "DELIVERED"},
                    "eta": null,
                    "tracking_url_provider": null
                  }
                }
                """);

        when(repo.findByTrackingNumberWithOrder("TRACK888")).thenReturn(Optional.of(shipment));
        when(repo.save(shipment)).thenReturn(shipment);

        shippingService.handleWebhook(payload);

        verifyNoInteractions(mail);
    }

    // ── sendStatusNotification (via applyTrackerUpdate) ───────────────────────

    @Test
    void handleWebhook_outForDelivery_sendsNotificationAndSetsBit2() throws Exception {
        UUID userId    = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        User user      = buildUser(userId);
        Order order    = buildOrder(orderId, user);
        // Was in_transit, now out_for_delivery → status changes → notification bit 2
        Shipment shipment = buildShipment(shipmentId, order, "TRACK777", ShipmentStatus.in_transit);
        shipment.setNotificationsSent(1); // bit 0 already set (shipped)

        JsonNode payload = json("""
                {
                  "event": "track_updated",
                  "data": {
                    "tracking_number": "TRACK777",
                    "tracking_status": {"status": "TRANSIT"},
                    "eta": null,
                    "tracking_url_provider": null
                  }
                }
                """);

        // Use a payload that maps to out_for_delivery — Shippo doesn't have that string,
        // so let's test delivered instead (bit 4).
        // Actually Shippo status "DELIVERED" → ShipmentStatus.delivered, which is bit 4.
        JsonNode deliveredPayload = json("""
                {
                  "event": "track_updated",
                  "data": {
                    "tracking_number": "TRACK777",
                    "tracking_status": {"status": "DELIVERED"},
                    "eta": null,
                    "tracking_url_provider": null
                  }
                }
                """);

        when(repo.findByTrackingNumberWithOrder("TRACK777")).thenReturn(Optional.of(shipment));
        when(repo.save(shipment)).thenReturn(shipment);

        shippingService.handleWebhook(deliveredPayload);

        verify(mail).sendShippingNotification(eq(order), eq(user), eq(shipment), eq("delivered"));
        verify(repo).updateNotificationsSent(shipmentId, 1 | 4); // bit 4 added
    }

    @Test
    void handleWebhook_deliveredAlreadyNotified_doesNotSendAgain() throws Exception {
        UUID userId    = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        User user      = buildUser(userId);
        Order order    = buildOrder(orderId, user);
        Shipment shipment = buildShipment(shipmentId, order, "TRACK666", ShipmentStatus.in_transit);
        shipment.setNotificationsSent(5); // bits 0 and 2 set (shipped + delivered already sent)

        JsonNode payload = json("""
                {
                  "event": "track_updated",
                  "data": {
                    "tracking_number": "TRACK666",
                    "tracking_status": {"status": "DELIVERED"},
                    "eta": null,
                    "tracking_url_provider": null
                  }
                }
                """);

        when(repo.findByTrackingNumberWithOrder("TRACK666")).thenReturn(Optional.of(shipment));
        when(repo.save(shipment)).thenReturn(shipment);

        shippingService.handleWebhook(payload);

        // bit 2 (value 4) is already set in notificationsSent=5 (binary 101), so no mail
        verify(mail, never()).sendShippingNotification(any(), any(), any(), eq("delivered"));
    }
}
