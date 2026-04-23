package com.harmony.store.service;

import com.harmony.store.config.JwtService;
import com.harmony.store.dto.LoginResponse;
import com.harmony.store.dto.RegisterRequest;
import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UsersService     usersService;
    @Mock JwtService       jwtService;
    @Mock PasswordEncoder  passwordEncoder;
    @Mock MailService      mailService;
    @Mock TwoFactorService twoFactorService;

    @InjectMocks AuthService authService;

    private User buildUser(UUID id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .name("Test User")
                .passwordHash("hashed")
                .role(UserRole.customer)
                .build();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_encodesPasswordAndCreatesUser() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@example.com");
        req.setName("Alice");
        req.setPassword("secret");

        UUID id = UUID.randomUUID();
        User user = buildUser(id, "alice@example.com");

        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");
        when(usersService.create("alice@example.com", "Alice", "hashed-secret")).thenReturn(user);
        when(jwtService.generateToken(any(), any(), any())).thenReturn("jwt-token");

        LoginResponse response = authService.register(req);

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        verify(passwordEncoder).encode("secret");
        verify(usersService).create("alice@example.com", "Alice", "hashed-secret");
    }

    // ── loginWithPassword ─────────────────────────────────────────────────────

    @Test
    void loginWithPassword_validCredentials_returnsToken() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "bob@example.com");

        when(usersService.findByEmail("bob@example.com")).thenReturn(user);
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(twoFactorService.getStatus(id)).thenReturn(mockDisabled2FA());
        when(jwtService.generateToken(any(), any(), any())).thenReturn("access-token");

        LoginResponse response = authService.loginWithPassword("bob@example.com", "pass");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.isRequires2FA()).isFalse();
    }

    @Test
    void loginWithPassword_userNotFound_throwsUnauthorized() {
        when(usersService.findByEmail("nobody@example.com")).thenReturn(null);

        assertThatThrownBy(() -> authService.loginWithPassword("nobody@example.com", "pass"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginWithPassword_wrongPassword_throwsUnauthorized() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "bob@example.com");

        when(usersService.findByEmail("bob@example.com")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.loginWithPassword("bob@example.com", "wrong"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginWithPassword_noPasswordHash_throwsUnauthorized() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("oauth@example.com")
                .role(UserRole.customer)
                .build(); // no passwordHash

        when(usersService.findByEmail("oauth@example.com")).thenReturn(user);

        assertThatThrownBy(() -> authService.loginWithPassword("oauth@example.com", "pass"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ── login (2FA branch) ────────────────────────────────────────────────────

    @Test
    void login_with2FAEnabled_returnsPendingResponse() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "carol@example.com");

        TwoFactorService.TwoFactorStatus status =
                new TwoFactorService.TwoFactorStatus(true, false, false, null, java.util.List.of("totp"), "totp", true);
        TwoFactorService.InitiatedLogin initiated = new TwoFactorService.InitiatedLogin(
                java.util.List.of("totp"), "totp", null);

        when(twoFactorService.getStatus(id)).thenReturn(status);
        when(twoFactorService.initiate2FALogin(user)).thenReturn(initiated);
        when(jwtService.generatePendingToken(any(), any(), any())).thenReturn("pending-token");

        LoginResponse response = authService.login(user);

        assertThat(response.isRequires2FA()).isTrue();
        assertThat(response.getPendingToken()).isEqualTo("pending-token");
        assertThat(response.getMethods()).containsExactly("totp");
    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Test
    void forgotPassword_existingUser_setsTokenAndSendsMail() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "dave@example.com");

        when(usersService.findByEmail("dave@example.com")).thenReturn(user);

        authService.forgotPassword("dave@example.com");

        verify(usersService).setResetToken(eq(id), anyString(), any(Instant.class));
        verify(mailService).sendPasswordReset(eq(user), anyString());
    }

    @Test
    void forgotPassword_unknownEmail_doesNothing() {
        when(usersService.findByEmail("ghost@example.com")).thenReturn(null);

        authService.forgotPassword("ghost@example.com");

        verifyNoInteractions(mailService);
        verify(usersService, never()).setResetToken(any(), any(), any());
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_validToken_updatesPassword() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "eve@example.com");

        when(usersService.findByResetToken("raw-token")).thenReturn(user);
        when(passwordEncoder.encode("newpass")).thenReturn("hashed-new");

        authService.resetPassword("raw-token", "newpass");

        verify(usersService).updatePassword(id, "hashed-new");
    }

    @Test
    void resetPassword_invalidToken_throwsBadRequest() {
        when(usersService.findByResetToken("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "newpass"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── verify2FA ─────────────────────────────────────────────────────────────

    @Test
    void verify2FA_invalidPendingToken_throwsUnauthorized() {
        when(jwtService.isTokenValid("bad-pending")).thenReturn(false);

        assertThatThrownBy(() -> authService.verify2FA("bad-pending", "123456", "totp"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verify2FA_validToken_returnsFullResponse() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "frank@example.com");

        when(jwtService.isTokenValid("pending")).thenReturn(true);
        when(jwtService.isPendingToken("pending")).thenReturn(true);
        when(jwtService.extractUserId("pending")).thenReturn(id.toString());
        when(usersService.findById(id)).thenReturn(user);
        when(jwtService.generateToken(any(), any(), any())).thenReturn("full-token");

        LoginResponse response = authService.verify2FA("pending", "123456", "totp");

        assertThat(response.getAccessToken()).isEqualTo("full-token");
        verify(twoFactorService).verifyLogin(id, "123456", "totp");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TwoFactorService.TwoFactorStatus mockDisabled2FA() {
        return new TwoFactorService.TwoFactorStatus(false, false, false, null, java.util.List.of(), null, false);
    }
}
