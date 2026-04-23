package com.harmony.store.controller;

import com.harmony.store.config.JwtService;
import com.harmony.store.config.OAuth2SuccessHandler;
import com.harmony.store.config.UserPrincipal;
import com.harmony.store.model.Order;
import com.harmony.store.model.OrderStatus;
import com.harmony.store.service.OrdersService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrdersController.class)
class OrdersControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean OrdersService svc;
    @MockBean JwtService jwtService;
    @MockBean OAuth2SuccessHandler oAuth2SuccessHandler;

    static final UUID USER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static UsernamePasswordAuthenticationToken customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(USER_ID.toString(), "user@example.com", "customer"),
                null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private static UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(ADMIN_ID.toString(), "admin@example.com", "admin"),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Order buildOrder(UUID id, OrderStatus status) {
        return Order.builder().id(id).status(status).total(new BigDecimal("99.00")).build();
    }

    // ── Customer endpoints ────────────────────────────────────────────────────

    @Test
    void findMine_authenticated_returns200() throws Exception {
        when(svc.findByUser(USER_ID)).thenReturn(List.of(buildOrder(UUID.randomUUID(), OrderStatus.paid)));

        mockMvc.perform(get("/orders").with(authentication(customerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("paid"));
    }

    @Test
    void findOne_authenticated_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(svc.findOne(orderId, USER_ID)).thenReturn(buildOrder(orderId, OrderStatus.paid));

        mockMvc.perform(get("/orders/{id}", orderId).with(authentication(customerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    @Test
    void findBySessionId_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(svc.findBySessionId("sess_abc")).thenReturn(buildOrder(orderId, OrderStatus.paid));

        mockMvc.perform(get("/orders/session/{sessionId}", "sess_abc").with(authentication(customerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @Test
    void findAll_asAdmin_returns200() throws Exception {
        when(svc.findAll(null)).thenReturn(List.of(buildOrder(UUID.randomUUID(), OrderStatus.paid)));

        mockMvc.perform(get("/orders/admin/all").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    //@Test TODO FixMe
    void findAll_asCustomer_returns403() throws Exception {
        mockMvc.perform(get("/orders/admin/all").with(authentication(customerAuth())))
                .andExpect(status().isForbidden());
    }

    //@Test TODO FixMe
    void updateStatus_asAdmin_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(svc.updateStatus(eq(orderId), eq(OrderStatus.shipped)))
                .thenReturn(buildOrder(orderId, OrderStatus.shipped));

        mockMvc.perform(
                put("/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"shipped"}
                                """)
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("shipped"));
    }

    @Test
    void updateStatus_asCustomer_returns403() throws Exception {
        mockMvc.perform(
                put("/orders/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"shipped"}
                                """)
                        .with(authentication(customerAuth())))
                .andExpect(status().isForbidden());
    }
}
