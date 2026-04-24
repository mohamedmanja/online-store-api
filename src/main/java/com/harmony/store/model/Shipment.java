package com.harmony.store.model;

import com.harmony.store.model.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.harmony.store.model.ShipmentStatus;

@Entity
@Table(name = "shipments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "shippo_shipment_id")
    private String shippoShipmentId;

    @Column(nullable = false)
    private String carrier;

    @Column(name = "service_code")
    private String serviceCode;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "label_url", columnDefinition = "TEXT")
    private String labelUrl;

    @Column(name = "label_format")
    private String labelFormat;

    @Column(name = "tracking_url", columnDefinition = "TEXT")
    private String trackingUrl;

    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(precision = 6, scale = 2)
    private BigDecimal length;

    @Column(precision = 6, scale = 2)
    private BigDecimal width;

    @Column(precision = 6, scale = 2)
    private BigDecimal height;

    @Column(name = "rate_amount", precision = 8, scale = 2)
    private BigDecimal rateAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_status", nullable = false)
    @Builder.Default
    private ShipmentStatus trackingStatus = ShipmentStatus.label_created;

    @Column(name = "tracking_detail", columnDefinition = "TEXT")
    private String trackingDetail;

    @Column(name = "estimated_delivery")
    private Instant estimatedDelivery;

    @Column(name = "last_tracked_at")
    private Instant lastTrackedAt;

    /** Bitmask: bit 0 = shipped, bit 1 = out_for_delivery, bit 2 = delivered */
    @Column(name = "notifications_sent", nullable = false)
    @Builder.Default
    private int notificationsSent = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
