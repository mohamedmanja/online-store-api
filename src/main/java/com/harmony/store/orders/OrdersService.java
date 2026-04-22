package com.harmony.store.orders;

import com.harmony.store.mail.MailService;
import com.harmony.store.products.Product;
import com.harmony.store.products.ProductRepository;
import com.harmony.store.users.User;
import com.harmony.store.users.UserRepository;
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
        // Idempotency — Stripe may deliver same event more than once
        orderRepo.findByStripeSessionId(dto.getStripeSessionId()).ifPresent(existing -> {
            log.warn("Duplicate webhook for session {}", dto.getStripeSessionId());
            throw new DuplicateSessionException(existing);
        });

        User user = dto.getUserId() != null
                ? userRepo.findById(UUID.fromString(dto.getUserId())).orElse(null)
                : null;

        Order order = Order.builder()
                .user(user)
                .stripeSessionId(dto.getStripeSessionId())
                .status(OrderStatus.paid)
                .total(BigDecimal.valueOf(dto.getTotal()))
                .shippingAddress(dto.getShippingAddress())
                .build();

        Order saved = orderRepo.save(order);

        List<OrderItem> items = new ArrayList<>();
        for (CreateOrderFromSessionDto.CheckoutItem item : dto.getItems()) {
            Product product = item.getProductId() != null
                    ? productRepo.findById(UUID.fromString(item.getProductId())).orElse(null)
                    : null;

            OrderItem orderItem = OrderItem.builder()
                    .order(saved)
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(BigDecimal.valueOf(item.getPrice()))
                    .build();
            items.add(itemRepo.save(orderItem));

            if (product != null) {
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
