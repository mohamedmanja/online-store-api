package com.harmony.store.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
    List<Order> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id AND o.user.id = :userId")
    Optional<Order> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<Order> findByStripeSessionId(String stripeSessionId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product ORDER BY o.createdAt DESC")
    List<Order> findAllWithDetails();

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.status = :status ORDER BY o.createdAt DESC")
    List<Order> findByStatus(@Param("status") OrderStatus status);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.stripeSessionId = :sessionId")
    Optional<Order> findByStripeSessionIdWithDetails(@Param("sessionId") String sessionId);
}
