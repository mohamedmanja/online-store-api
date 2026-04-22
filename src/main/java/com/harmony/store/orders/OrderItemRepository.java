package com.harmony.store.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
}
