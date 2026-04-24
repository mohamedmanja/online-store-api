package com.harmony.store.service;

import com.harmony.store.service.MailService;
import com.harmony.store.model.Product;
import com.harmony.store.repository.ProductRepository;
import com.harmony.store.model.User;
import com.harmony.store.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.harmony.store.dto.CreateOrderFromSessionDto;
import com.harmony.store.model.Order;
import com.harmony.store.model.OrderItem;
import com.harmony.store.model.OrderStatus;
import com.harmony.store.repository.OrderItemRepository;
import com.harmony.store.repository.OrderRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrdersService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository itemRepo;
    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final MailService mailService;

    // ── Create from Stripe webhook ────────────────────────────────────────────

    @Transactional
    public Order createFromStripeSession(CreateOrderFromSessionDto dto) {
        log.info("createFromStripeSession — sessionId: {}, userId: {}, total: {}, itemCount: {}",
                dto.getStripeSessionId(), dto.getUserId(), dto.getTotal(),
                dto.getItems() != null ? dto.getItems().size() : 0);

        // Idempotency — Stripe may deliver same event more than once
        orderRepo.findByStripeSessionId(dto.getStripeSessionId()).ifPresent(existing -> {
            log.warn("Duplicate webhook for session {}", dto.getStripeSessionId());
            throw new DuplicateSessionException(existing);
        });

        User user = null;
        if (dto.getUserId() != null) {
            user = userRepo.findById(UUID.fromString(dto.getUserId())).orElse(null);
            if (user == null) {
                log.warn("User not found for id {} — order will be saved without user", dto.getUserId());
            } else {
                log.info("Resolved user: id={}, email={}", user.getId(), user.getEmail());
            }
        }

        Order order = Order.builder()
                .user(user)
                .stripeSessionId(dto.getStripeSessionId())
                .status(OrderStatus.paid)
                .total(BigDecimal.valueOf(dto.getTotal()))
                .shippingAddress(dto.getShippingAddress())
                .build();

        log.info("Saving order — sessionId: {}, status: {}, total: {}", order.getStripeSessionId(), order.getStatus(), order.getTotal());
        Order saved = orderRepo.save(order);
        log.info("Order saved — id: {}", saved.getId());

        List<OrderItem> items = new ArrayList<>();
        for (CreateOrderFromSessionDto.CheckoutItem item : dto.getItems()) {
            log.info("Processing item — productId: {}, quantity: {}, price: {}",
                    item.getProductId(), item.getQuantity(), item.getPrice());

            Product product = item.getProductId() != null
                    ? productRepo.findById(UUID.fromString(item.getProductId())).orElse(null)
                    : null;

            if (product == null) {
                log.warn("Product not found for id {} — order item will be saved without product reference", item.getProductId());
            } else {
                log.info("Resolved product: id={}, name={}, stock={}", product.getId(), product.getName(), product.getStock());
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(saved)
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(BigDecimal.valueOf(item.getPrice()))
                    .build();
            OrderItem savedItem = itemRepo.save(orderItem);
            log.info("OrderItem saved — id: {}", savedItem.getId());
            items.add(savedItem);

            if (product != null) {
                log.info("Decrementing stock — productId: {}, quantity: {}", product.getId(), item.getQuantity());
                productRepo.decrementStock(product.getId(), item.getQuantity());
            }
        }

        log.info("Order {} created — {} item(s), ${}", saved.getId(), items.size(), dto.getTotal());

        // Send confirmation email asynchronously (best-effort)
        if (user != null) {
            final User finalUser = user;
            orderRepo.findByIdAndUserId(saved.getId(), finalUser.getId()).ifPresent(orderWithItems ->
                    mailService.sendOrderConfirmation(orderWithItems, finalUser));
        }

        return saved;
    }

    // ── Duplicate session wrapper ─────────────────────────────────────────────

    public static class DuplicateSessionException extends RuntimeException {
        final Order existing;
        DuplicateSessionException(Order existing) {
            super("Duplicate");
            this.existing = existing;
        }
    }

    // ── Query methods ─────────────────────────────────────────────────────────

    public List<Order> findByUser(UUID userId) {
        return orderRepo.findByUserId(userId);
    }

    public Order findOne(UUID id, UUID userId) {
        return orderRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order " + id + " not found"));
    }

    public Order findBySessionId(String sessionId) {
        return orderRepo.findByStripeSessionIdWithDetails(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order with session id " + sessionId + " not found"));
    }

    public List<Order> findAll(OrderStatus status) {
        return status != null
                ? orderRepo.findByStatus(status)
                : orderRepo.findAllWithDetails();
    }

    @Transactional
    public Order updateStatus(UUID id, OrderStatus status) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order " + id + " not found"));
        order.setStatus(status);
        return orderRepo.save(order);
    }
}
