package com.harmony.store.controller;

import com.harmony.store.dto.*;
import com.harmony.store.config.CookieUtils;
import com.harmony.store.config.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.harmony.store.dto.ForgotPasswordRequest;
import com.harmony.store.dto.LoginRequest;
import com.harmony.store.dto.LoginResponse;
import com.harmony.store.dto.RegisterRequest;
import com.harmony.store.dto.ResendOtpRequest;
import com.harmony.store.dto.ResetPasswordRequest;
import com.harmony.store.dto.VerifyOtpRequest;
import com.harmony.store.service.AuthService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ── Email / password ──────────────────────────────────────────────────────

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest req,
                                         HttpServletResponse response) {
        LoginResponse result = authService.register(req);
        CookieUtils.setAuthCookie(response, result.getAccessToken());
        return Map.of("user", result.getUser());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                    HttpServletResponse response) {
        LoginResponse result = authService.loginWithPassword(req.getEmail(), req.getPassword());
        if (result.isRequires2FA()) {
            return ResponseEntity.ok(result);
        }
        CookieUtils.setAuthCookie(response, result.getAccessToken());
        return ResponseEntity.ok(Map.of("user", result.getUser()));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletResponse response) {
        CookieUtils.clearAuthCookie(response);
        return Map.of("message", "Logged out");
    }

    @GetMapping("/me")
    public UserPrincipal me(@AuthenticationPrincipal UserPrincipal principal) {
        return principal;
    }

    // ── 2FA login verify / resend ─────────────────────────────────────────────

    @PostMapping("/2fa/verify")
    public Map<String, Object> verify2FA(@Valid @RequestBody VerifyOtpRequest req,
                                          HttpServletResponse response) {
        LoginResponse result = authService.verify2FA(req.getPendingToken(), req.getCode(), req.getMethod());
        CookieUtils.setAuthCookie(response, result.getAccessToken());
        return Map.of("user", result.getUser());
    }

    @PostMapping("/2fa/resend")
    public Map<String, String> resend2FA(@Valid @RequestBody ResendOtpRequest req) {
        String message = authService.resend2FA(req.getPendingToken(), req.getMethod());
        return Map.of("message", message);
    }

    // ── Password reset ────────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.getEmail());
        return Map.of("message", "If an account with that email exists, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.getToken(), req.getPassword());
        return Map.of("message", "Password updated successfully.");
    }
}
