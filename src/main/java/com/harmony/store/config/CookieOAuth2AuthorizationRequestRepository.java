package com.harmony.store.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Stateless OAuth2 authorization-request repository backed by a signed cookie.
 *
 * Why this exists:
 *   Spring Security's default HttpSessionOAuth2AuthorizationRequestRepository stores
 *   the OAuth2 state in the server-side HttpSession. In a Kubernetes deployment with
 *   multiple replicas and no sticky sessions, the /auth/oauth2/google redirect can be
 *   handled by Pod A (state stored there) while Google's callback lands on Pod B
 *   (no session, state missing → "Authorization Request not found").
 *
 * Solution:
 *   Serialize the OAuth2AuthorizationRequest to a short-lived, HMAC-signed, httpOnly
 *   cookie. Any pod can deserialize and verify it — no shared storage needed.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME    = "_oauth2_auth_req";
    private static final int    MAX_AGE_SECS   = 10 * 60; // 10 minutes
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${app.oauth.state-secret}")
    private String secret;

    // ── Store (called on /auth/oauth2/google) ────────────────────────────────

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }

        String signed = serialize(authorizationRequest);
        boolean secure = request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));

        Cookie cookie = new Cookie(COOKIE_NAME, signed);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE_SECS);
        cookie.setSecure(secure);
        response.addCookie(cookie);
    }

    // ── Load (called on /auth/google/callback) ───────────────────────────────

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String value = getCookieValue(request);
        if (value == null) return null;
        return deserialize(value);
    }

    // ── Remove (called after callback, success or failure) ───────────────────

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                  HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = loadAuthorizationRequest(request);
        clearCookie(response);
        return authRequest;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String serialize(OAuth2AuthorizationRequest req) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(req);
            byte[] payload = bos.toByteArray();
            byte[] mac     = hmac(payload);
            // Format: base64(payload) + "." + base64(mac)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize OAuth2AuthorizationRequest", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        int dot = value.lastIndexOf('.');
        if (dot == -1) return null;
        try {
            byte[] payload      = Base64.getUrlDecoder().decode(value.substring(0, dot));
            byte[] receivedMac  = Base64.getUrlDecoder().decode(value.substring(dot + 1));
            byte[] expectedMac  = hmac(payload);

            // Constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(expectedMac, receivedMac)) return null;

            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(payload))) {
                return (OAuth2AuthorizationRequest) ois.readObject();
            }
        } catch (Exception e) {
            return null; // tampered or expired — treat as missing
        }
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(), HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC init failed", e);
        }
    }

    private String getCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private void clearCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
