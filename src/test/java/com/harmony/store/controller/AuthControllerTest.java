package com.harmony.store.controller;

import com.harmony.store.config.JwtService;
import com.harmony.store.config.OAuth2SuccessHandler;
import com.harmony.store.config.UserPrincipal;
import com.harmony.store.dto.LoginResponse;
import com.harmony.store.service.AuthService;
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

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AuthService authService;
    @MockBean JwtService jwtService;                      // required by JwtAuthFilter bean
    @MockBean OAuth2SuccessHandler oAuth2SuccessHandler;  // required by SecurityConfig

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static UsernamePasswordAuthenticationToken customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(USER_ID.toString(), "user@example.com", "customer"),
                null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private static LoginResponse.UserInfo userInfo() {
        return LoginResponse.UserInfo.builder()
                .id(USER_ID.toString()).email("user@example.com").name("Test").role("customer").build();
    }

    // ── register ──────────────────────────────────────────────────────────────

    //@Test TODO FixMe
    void register_validRequest_returns201WithUser() throws Exception {
        when(authService.register(any())).thenReturn(
                LoginResponse.builder().accessToken("tok").user(userInfo()).build());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test","email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("user@example.com"));
    }

    //@Test TODO FixMe
    void register_invalidRequest_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"bad","password":"x"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    //@Test TODO FixMe
    void login_noTwoFa_returns200WithUser() throws Exception {
        when(authService.loginWithPassword(anyString(), anyString())).thenReturn(
                LoginResponse.builder().accessToken("tok").user(userInfo()).build());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("user@example.com"));
    }

    //@Test TODO FixMe
    void login_requires2FA_returns200WithPendingToken() throws Exception {
        when(authService.loginWithPassword(anyString(), anyString())).thenReturn(
                LoginResponse.builder()
                        .requires2FA(true)
                        .pendingToken("pending")
                        .methods(List.of("email"))
                        .defaultMethod("email")
                        .build());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2FA").value(true))
                .andExpect(jsonPath("$.pendingToken").value("pending"));
    }

    // ── logout ────────────────────────────────────────────────────────────────

    //@Test TODO FixMe
    void logout_returns200WithMessage() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out"));
    }

    // ── me ────────────────────────────────────────────────────────────────────

    @Test
    void me_authenticated_returnsPrincipal() throws Exception {
        mockMvc.perform(get("/auth/me").with(authentication(customerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    // ── 2FA ───────────────────────────────────────────────────────────────────

    //@Test TODO FixMe
    void verify2FA_returns200WithUser() throws Exception {
        when(authService.verify2FA(anyString(), anyString(), anyString())).thenReturn(
                LoginResponse.builder().accessToken("tok").user(userInfo()).build());

        mockMvc.perform(post("/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pendingToken":"pend","code":"123456","method":"email"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").exists());
    }

    //@Test TODO FixMe
    void resend2FA_returns200WithMessage() throws Exception {
        when(authService.resend2FA(anyString(), anyString())).thenReturn("OTP sent");

        mockMvc.perform(post("/auth/2fa/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pendingToken":"pend","method":"email"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP sent"));
    }

    // ── password reset ────────────────────────────────────────────────────────

    //@Test TODO FixMe
    void forgotPassword_returns200WithMessage() throws Exception {
        doNothing().when(authService).forgotPassword(anyString());

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    //@Test TODO FixMe
    void resetPassword_returns200WithMessage() throws Exception {
        doNothing().when(authService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"tok","password":"newpassword123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully."));
    }
}
