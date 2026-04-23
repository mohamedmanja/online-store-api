package com.harmony.store.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "supersecretjwtkeyforharmonystorethatisatleast256bits");
        ReflectionTestUtils.setField(jwtService, "expiryDays", 7L);
        ReflectionTestUtils.setField(jwtService, "pendingExpiryMinutes", 10L);
    }

    // ── generateToken ─────────────────────────────────────────────────────────

    @Test
    void generateToken_returnsNonNullToken() {
        String token = jwtService.generateToken("user-id", "test@example.com", "customer");
        assertThat(token).isNotBlank();
    }

    @Test
    void generateToken_extractsCorrectClaims() {
        String token = jwtService.generateToken("user-123", "alice@example.com", "admin");

        assertThat(jwtService.extractUserId(token)).isEqualTo("user-123");
        assertThat(jwtService.extractEmail(token)).isEqualTo("alice@example.com");
        assertThat(jwtService.extractRole(token)).isEqualTo("admin");
    }

    @Test
    void generateToken_isValid() {
        String token = jwtService.generateToken("user-id", "test@example.com", "customer");
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void generateToken_isNotPendingToken() {
        String token = jwtService.generateToken("user-id", "test@example.com", "customer");
        assertThat(jwtService.isPendingToken(token)).isFalse();
    }

    // ── generatePendingToken ──────────────────────────────────────────────────

    @Test
    void generatePendingToken_isPendingToken() {
        String token = jwtService.generatePendingToken("user-id", "test@example.com", "customer");
        assertThat(jwtService.isPendingToken(token)).isTrue();
    }

    @Test
    void generatePendingToken_isValid() {
        String token = jwtService.generatePendingToken("user-id", "test@example.com", "customer");
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void generatePendingToken_extractsCorrectUserId() {
        String token = jwtService.generatePendingToken("user-456", "bob@example.com", "customer");
        assertThat(jwtService.extractUserId(token)).isEqualTo("user-456");
    }

    // ── isTokenValid ──────────────────────────────────────────────────────────

    @Test
    void isTokenValid_returnsFalseForGarbage() {
        assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForEmptyString() {
        assertThat(jwtService.isTokenValid("")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForTamperedToken() {
        String token = jwtService.generateToken("user-id", "test@example.com", "customer");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    // ── isPendingToken ────────────────────────────────────────────────────────

    @Test
    void isPendingToken_returnsFalseForGarbage() {
        assertThat(jwtService.isPendingToken("garbage")).isFalse();
    }
}
