package com.harmony.store.service;

import com.harmony.store.dto.CreateOrderFromSessionDto;
import com.harmony.store.model.*;
import com.harmony.store.repository.OrderItemRepository;
import com.harmony.store.repository.OrderRepository;
import com.harmony.store.repository.ProductRepository;
import com.harmony.store.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrdersServiceTest {

    @Mock OrderRepository     orderRepo;
    @Mock OrderItemRepository itemRepo;
    @Mock UserRepository      userRepo;
    @Mock ProductRepository   productRepo;
    @Mock MailService         mailService;

    @InjectMocks OrdersService ordersService;

    private User buildUser(UUID id) {
        return User.builder().id(id).email("user@example.com").role(UserRole.customer).build();
    }

    private Product buildProduct(UUID id, int stock) {
        return Product.builder()
                .id(id)
                .name("Widget")
                .price(BigDecimal.valueOf(10.00))
                .stock(stock)
                .build();
    }

    private CreateOrderFromSessionDto buildDto(String sessionId, String userId, String productId) {
        CreateOrderFromSessionDto dto = new CreateOrderFromSessionDto();
        dto.setStripeSessionId(sessionId);
        dto.setUserId(userId);
        dto.setTotal(19.99);

        CreateOrderFromSessionDto.CheckoutItem item = new CreateOrderFromSessionDto.CheckoutItem();
        item.setProductId(productId);
        item.setQuantity(2);
        item.setPrice(9.99);
        dto.setItems(List.of(item));
        return dto;
    }

    // ── createFromStripeSession ───────────────────────────────────────────────

    @Test
    void createFromStripeSession_success_savesOrderAndItems() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        CreateOrderFromSessionDto dto = buildDto("sess_123", userId.toString(), productId.toString());
        User user       = buildUser(userId);
        Product product = buildProduct(productId, 10);
        Order saved     = Order.builder().id(orderId).stripeSessionId("sess_123")
                .status(OrderStatus.paid).total(BigDecimal.valueOf(19.99)).build();

        when(orderRepo.findByStripeSessionId("sess_123")).thenReturn(Optional.empty());
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepo.save(any(Order.class))).thenReturn(saved);
        when(productRepo.findById(productId)).thenReturn(Optional.of(product));
        when(itemRepo.save(any(OrderItem.class))).thenReturn(new OrderItem());
        when(orderRepo.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        Order result = ordersService.createFromStripeSession(dto);

        assertThat(result.getId()).isEqualTo(orderId);
        verify(orderRepo).save(any(Order.class));
        verify(itemRepo).save(any(OrderItem.class));
        verify(productRepo).decrementStock(productId, 2);
    }

    @Test
    void createFromStripeSession_duplicateSession_throwsDuplicateException() {
        Order existing = Order.builder().id(UUID.randomUUID()).build();
        when(orderRepo.findByStripeSessionId("sess_dup")).thenReturn(Optional.of(existing));

        CreateOrderFromSessionDto dto = buildDto("sess_dup", UUID.randomUUID().toString(), null);

        assertThatThrownBy(() -> ordersService.createFromStripeSession(dto))
                .isInstanceOf(OrdersService.DuplicateSessionException.class);

        verify(orderRepo, never()).save(any());
    }

    @Test
    void createFromStripeSession_unknownUser_savesOrderWithNullUser() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        CreateOrderFromSessionDto dto = buildDto("sess_nouser", userId.toString(), productId.toString());
        Order saved = Order.builder().id(orderId).build();

        when(orderRepo.findByStripeSessionId("sess_nouser")).thenReturn(Optional.empty());
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        when(orderRepo.save(any(Order.class))).thenReturn(saved);
        when(productRepo.findById(productId)).thenReturn(Optional.empty());
        when(itemRepo.save(any(OrderItem.class))).thenReturn(new OrderItem());

        Order result = ordersService.createFromStripeSession(dto);

        assertThat(result).isNotNull();
        verify(orderRepo).save(argThat(o -> o.getUser() == null));
    }

    @Test
    void createFromStripeSession_unknownProduct_savesItemWithNullProduct() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        CreateOrderFromSessionDto dto = buildDto("sess_noprod", userId.toString(), productId.toString());
        User user   = buildUser(userId);
        Order saved = Order.builder().id(orderId).build();

        when(orderRepo.findByStripeSessionId("sess_noprod")).thenReturn(Optional.empty());
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepo.save(any(Order.class))).thenReturn(saved);
        when(productRepo.findById(productId)).thenReturn(Optional.empty());
        when(itemRepo.save(any(OrderItem.class))).thenReturn(new OrderItem());
        when(orderRepo.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        ordersService.createFromStripeSession(dto);

        verify(productRepo, never()).decrementStock(any(), anyInt());
        verify(itemRepo).save(argThat(item -> item.getProduct() == null));
    }

    // ── findOne ───────────────────────────────────────────────────────────────

    @Test
    void findOne_existingOrder_returnsOrder() {
        UUID orderId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();
        Order order  = Order.builder().id(orderId).build();

        when(orderRepo.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        assertThat(ordersService.findOne(orderId, userId)).isEqualTo(order);
    }

    @Test
    void findOne_notFound_throwsNotFound() {
        UUID orderId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();

        when(orderRepo.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ordersService.findOne(orderId, userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_withStatus_delegatesToFindByStatus() {
        List<Order> orders = List.of(Order.builder().build());
        when(orderRepo.findByStatus(OrderStatus.paid)).thenReturn(orders);

        assertThat(ordersService.findAll(OrderStatus.paid)).isEqualTo(orders);
        verify(orderRepo).findByStatus(OrderStatus.paid);
        verify(orderRepo, never()).findAllWithDetails();
    }

    @Test
    void findAll_withoutStatus_delegatesToFindAllWithDetails() {
        List<Order> orders = List.of(Order.builder().build());
        when(orderRepo.findAllWithDetails()).thenReturn(orders);

        assertThat(ordersService.findAll(null)).isEqualTo(orders);
        verify(orderRepo, never()).findByStatus(any());
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_existingOrder_updatesAndSaves() {
        UUID orderId = UUID.randomUUID();
        Order order  = Order.builder().id(orderId).status(OrderStatus.paid).build();

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepo.save(order)).thenReturn(order);

        Order result = ordersService.updateStatus(orderId, OrderStatus.shipped);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.shipped);
        verify(orderRepo).save(order);
    }

    @Test
    void updateStatus_notFound_throwsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ordersService.updateStatus(orderId, OrderStatus.shipped))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── findBySessionId ───────────────────────────────────────────────────────

    @Test
    void findBySessionId_found_returnsOrder() {
        Order order = Order.builder().id(UUID.randomUUID()).stripeSessionId("sess_abc").build();
        when(orderRepo.findByStripeSessionIdWithDetails("sess_abc")).thenReturn(Optional.of(order));

        assertThat(ordersService.findBySessionId("sess_abc")).isEqualTo(order);
    }

    @Test
    void findBySessionId_notFound_throwsNotFound() {
        when(orderRepo.findByStripeSessionIdWithDetails("sess_x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ordersService.findBySessionId("sess_x"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
