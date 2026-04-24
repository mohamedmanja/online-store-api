package com.harmony.store.controller;

import com.harmony.store.config.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import com.harmony.store.service.TwoFactorService;

@RestController
@RequestMapping("/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService svc;

    @Data
    static class VerifyTotpRequest {
        @NotBlank @Size(min = 6, max = 6) private String code;
    }

    @Data
    static class PhoneSetupRequest {
        @NotBlank private String phoneNumber;
    }

    @Data
    static class DefaultMethodRequest {
        @NotBlank @Pattern(regexp = "totp|email|phone") private String method;
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @GetMapping("/status")
    public TwoFactorService.TwoFactorStatus status(@AuthenticationPrincipal UserPrincipal principal) {
        return svc.getStatus(UUID.fromString(principal.getId()));
    }

    // ── TOTP ──────────────────────────────────────────────────────────────────

    @PostMapping("/totp/setup")
    public Map<String, String> setupTotp(@AuthenticationPrincipal UserPrincipal principal) {
        return svc.setupTotp(UUID.fromString(principal.getId()));
    }

    @PostMapping("/totp/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enableTotp(@AuthenticationPrincipal UserPrincipal principal,
                            @Valid @RequestBody VerifyTotpRequest req) {
        svc.enableTotp(UUID.fromString(principal.getId()), req.getCode());
    }

    @DeleteMapping("/totp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableTotp(@AuthenticationPrincipal UserPrincipal principal) {
        svc.disableTotp(UUID.fromString(principal.getId()));
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    @PostMapping("/email/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enableEmail(@AuthenticationPrincipal UserPrincipal principal) {
        svc.enableEmail(UUID.fromString(principal.getId()));
    }

    @DeleteMapping("/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableEmail(@AuthenticationPrincipal UserPrincipal principal) {
        svc.disableEmail(UUID.fromString(principal.getId()));
    }

    // ── Phone ─────────────────────────────────────────────────────────────────

    @PostMapping("/phone/setup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setupPhone(@AuthenticationPrincipal UserPrincipal principal,
                            @Valid @RequestBody PhoneSetupRequest req) {
        svc.setupPhone(UUID.fromString(principal.getId()), req.getPhoneNumber());
    }

    @PostMapping("/phone/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enablePhone(@AuthenticationPrincipal UserPrincipal principal,
                             @Valid @RequestBody VerifyTotpRequest req) {
        svc.verifyPhoneSetup(UUID.fromString(principal.getId()), req.getCode());
    }

    @DeleteMapping("/phone")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disablePhone(@AuthenticationPrincipal UserPrincipal principal) {
        svc.disablePhone(UUID.fromString(principal.getId()));
    }

    // ── Default method ────────────────────────────────────────────────────────

    @PutMapping("/default-method")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefaultMethod(@AuthenticationPrincipal UserPrincipal principal,
                                  @Valid @RequestBody DefaultMethodRequest req) {
        svc.setDefaultMethod(UUID.fromString(principal.getId()), req.getMethod());
    }
}
