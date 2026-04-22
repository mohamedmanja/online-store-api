package com.harmony.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.harmony.store.model.Shipment;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    List<Shipment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    @Query("SELECT s FROM Shipment s LEFT JOIN FETCH s.order o LEFT JOIN FETCH o.user WHERE s.trackingNumber = :tn")
    Optional<Shipment> findByTrackingNumberWithOrder(@Param("tn") String trackingNumber);

    @Query("SELECT s FROM Shipment s LEFT JOIN FETCH s.order o LEFT JOIN FETCH o.user WHERE s.id = :id")
    Optional<Shipment> findByIdWithOrder(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("UPDATE Shipment s SET s.notificationsSent = :sent WHERE s.id = :id")
    void updateNotificationsSent(@Param("id") UUID id, @Param("sent") int sent);
}
