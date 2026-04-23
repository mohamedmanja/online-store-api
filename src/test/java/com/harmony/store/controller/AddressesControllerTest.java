package com.harmony.store.controller;

import com.harmony.store.config.JwtService;
import com.harmony.store.config.OAuth2SuccessHandler;
import com.harmony.store.config.UserPrincipal;
import com.harmony.store.model.Address;
import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import com.harmony.store.service.AddressesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AddressesController.class)
class AddressesControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AddressesService addressesService;
    @MockBean JwtService jwtService;
    @MockBean OAuth2SuccessHandler oAuth2SuccessHandler;

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static UsernamePasswordAuthenticationToken customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(USER_ID.toString(), "user@example.com", "customer"),
                null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private Address buildAddress(UUID id) {
        User user = User.builder().id(USER_ID).email("user@example.com").role(UserRole.customer).build();
        return Address.builder()
                .id(id).user(user).name("Home").line1("123 Main St")
                .city("Springfield").state("IL").postalCode("62701").country("US")
                .build();
    }

    @Test
    void list_authenticated_returns200() throws Exception {
        when(addressesService.findByUser(USER_ID)).thenReturn(List.of(buildAddress(UUID.randomUUID())));

        mockMvc.perform(get("/addresses").with(authentication(customerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("Springfield"));
    }

    //@Test TODO FixMe
    void create_authenticated_returns201() throws Exception {
        when(addressesService.create(eq(USER_ID), any())).thenReturn(buildAddress(UUID.randomUUID()));

        mockMvc.perform(
                post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Home","line1":"123 Main St",
                                  "city":"Springfield","state":"IL",
                                  "postalCode":"62701","country":"US"
                                }
                                """)
                        .with(authentication(customerAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.line1").value("123 Main St"));
    }

    //@Test TODO FixMe
    void create_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(
                post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Home"}
                                """)
                        .with(authentication(customerAuth())))
                .andExpect(status().isBadRequest());
    }

    //@Test TODO FixMe
    void update_authenticated_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Address updated = buildAddress(id);
        updated.setCity("Shelbyville");
        when(addressesService.update(eq(USER_ID), eq(id), any())).thenReturn(updated);

        mockMvc.perform(
                put("/addresses/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "line1":"456 Oak Ave","city":"Shelbyville",
                                  "state":"IL","postalCode":"62565","country":"US"
                                }
                                """)
                        .with(authentication(customerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Shelbyville"));
    }

    //@Test TODO FixMe
    void delete_authenticated_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(addressesService).delete(USER_ID, id);

        mockMvc.perform(delete("/addresses/{id}", id).with(authentication(customerAuth())))
                .andExpect(status().isNoContent());
    }
}
