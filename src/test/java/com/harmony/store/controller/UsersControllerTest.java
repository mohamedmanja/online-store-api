package com.harmony.store.controller;

import com.harmony.store.config.JwtService;
import com.harmony.store.config.OAuth2SuccessHandler;
import com.harmony.store.config.UserPrincipal;
import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import com.harmony.store.service.UsersService;
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

@WebMvcTest(UsersController.class)
class UsersControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean UsersService usersService;
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

    private User buildUser(UUID id, String email, UserRole role) {
        return User.builder().id(id).email(email).name("Test").role(role).build();
    }

    // ── Customer endpoints ────────────────────────────────────────────────────

    @Test
    void getMe_authenticated_returnsUser() throws Exception {
        when(usersService.findById(USER_ID)).thenReturn(buildUser(USER_ID, "user@example.com", UserRole.customer));

        mockMvc.perform(get("/users/me").with(authentication(customerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    //@Test TODO FixMe
    void updateMe_authenticated_returnsUpdatedUser() throws Exception {
        User updated = buildUser(USER_ID, "user@example.com", UserRole.customer);
        updated.setName("New Name");
        when(usersService.update(eq(USER_ID), any())).thenReturn(updated);

        mockMvc.perform(
                put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Name"}
                                """)
                        .with(authentication(customerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    //@Test TODO FixMe
    void changePassword_authenticated_returns204() throws Exception {
        doNothing().when(usersService).changePassword(eq(USER_ID), any());

        mockMvc.perform(
                put("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old","newPassword":"newpassword123"}
                                """)
                        .with(authentication(customerAuth())))
                .andExpect(status().isNoContent());
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @Test
    void getAllUsers_asAdmin_returns200() throws Exception {
        when(usersService.findAll()).thenReturn(List.of(
                buildUser(USER_ID,  "user@example.com",  UserRole.customer),
                buildUser(ADMIN_ID, "admin@example.com", UserRole.admin)));

        mockMvc.perform(get("/users").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    //@Test TODO FixMe
    void getAllUsers_asCustomer_returns403() throws Exception {
        mockMvc.perform(get("/users").with(authentication(customerAuth())))
                .andExpect(status().isForbidden());
    }

    //@Test TODO FixMe
    void updateRole_asAdmin_returns200() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(usersService.updateRole(eq(targetId), eq(UserRole.admin)))
                .thenReturn(buildUser(targetId, "target@example.com", UserRole.admin));

        mockMvc.perform(
                put("/users/{id}/role", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"admin"}
                                """)
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    void updateRole_asCustomer_returns403() throws Exception {
        mockMvc.perform(
                put("/users/{id}/role", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"admin"}
                                """)
                        .with(authentication(customerAuth())))
                .andExpect(status().isForbidden());
    }
}
