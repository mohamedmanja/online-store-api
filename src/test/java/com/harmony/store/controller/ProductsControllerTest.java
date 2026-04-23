package com.harmony.store.controller;

import com.harmony.store.config.JwtService;
import com.harmony.store.config.OAuth2SuccessHandler;
import com.harmony.store.config.UserPrincipal;
import com.harmony.store.model.Category;
import com.harmony.store.model.Product;
import com.harmony.store.service.ProductsService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductsController.class)
class ProductsControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ProductsService svc;
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

    private Product buildProduct(UUID id) {
        return Product.builder().id(id).name("Widget").price(new BigDecimal("9.99")).stock(10).build();
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    //@Test TODO FixMe
    void findAll_noAuth_returns200() throws Exception {
        when(svc.findAll(any())).thenReturn(Map.of("products", List.of(), "total", 0L));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray());
    }

    //@Test TODO FixMe
    void findOne_noAuth_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(svc.findOne(id)).thenReturn(buildProduct(id));

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Widget"));
    }

    //@Test TODO FixMe
    void findCategories_noAuth_returns200() throws Exception {
        Category cat = Category.builder().id(UUID.randomUUID()).name("Electronics").slug("electronics").build();
        when(svc.findAllCategories()).thenReturn(List.of(cat));

        mockMvc.perform(get("/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("electronics"));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    //@Test TODO FixMe
    void create_asAdmin_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(svc.create(any(), any())).thenReturn(buildProduct(id));

        mockMvc.perform(
                multipart("/products")
                        .param("name", "Widget")
                        .param("price", "9.99")
                        .param("stock", "10")
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Widget"));
    }

    @Test
    void create_asCustomer_returns403() throws Exception {
        mockMvc.perform(
                multipart("/products")
                        .param("name", "Widget")
                        .param("price", "9.99")
                        .param("stock", "10")
                        .with(authentication(customerAuth())))
                .andExpect(status().isForbidden());
    }

    //@Test TODO FixMe
    void delete_asAdmin_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(svc).remove(id);

        mockMvc.perform(delete("/products/{id}", id).with(authentication(adminAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_asCustomer_returns403() throws Exception {
        mockMvc.perform(delete("/products/{id}", UUID.randomUUID()).with(authentication(customerAuth())))
                .andExpect(status().isForbidden());
    }

    //@Test TODO FixMe
    void createCategory_asAdmin_returns201() throws Exception {
        Category cat = Category.builder().id(UUID.randomUUID()).name("Books").slug("books").build();
        when(svc.createCategory(any())).thenReturn(cat);

        mockMvc.perform(
                post("/products/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Books","slug":"books"}
                                """)
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("books"));
    }
}
