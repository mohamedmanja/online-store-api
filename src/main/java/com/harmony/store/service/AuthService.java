package com.harmony.store.service;

import com.harmony.store.dto.LoginResponse;
import com.harmony.store.dto.RegisterRequest;
import com.harmony.store.config.JwtService;
import com.harmony.store.service.MailService;
import com.harmony.store.model.User;
import com.harmony.store.service.UsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import com.harmony.store.service.TwoFactorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsersService     usersService;
    private final JwtService       jwtService;
    private final PasswordEncoder  passwordEncoder;
    private final MailService      mailService;
    private final TwoFactorService twoFactorService;

    public LoginResponse register(RegisterRequest req) {
        String hash = passwordEncoder.encode(req.getPassword());
        User user = usersService.create(req.getEmail(), req.getName(), hash);
        return buildFullResponse(user);
    }

    public LoginResponse loginWithPassword(String email, String password) {
        User user = usersService.findByEmail(email);
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return login(user);
    }

    public LoginResponse login(User user) {
        var status = twoFactorService.getStatus(user.getId());
        if (status.anyEnabled()) {
            var initiated = twoFactorService.initiate2FALogin(user);
            String pending = jwtService.generatePendingToken(
                    user.getId().toString(), user.getEmail(), user.getRole().name());
            return LoginResponse.builder()
                    .requires2FA(true)
                    .pendingToken(pending)
                    .methods(initiated.methods())
                    .defaultMethod(initiated.defaultMethod())
                    .hint(initiated.hint())
                    .build();
        }
        return buildFullResponse(user);
    }

    public LoginResponse verify2FA(String pendingToken, String code, String method) {
        if (!jwtService.isTokenValid(pendingToken) || !jwtService.isPendingToken(pendingToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired — please log in again");
        }
        UUID userId = UUID.fromString(jwtService.extractUserId(pendingToken));
        twoFactorService.verifyLogin(userId, code, method);
        User user = usersService.findById(userId);
        return buildFullResponse(user);
    }

    public String resend2FA(String pendingToken, String method) {
        if (!jwtService.isTokenValid(pendingToken) || !jwtService.isPendingToken(pendingToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
        }
        UUID userId = UUID.fromString(jwtService.extractUserId(pendingToken));
        return twoFactorService.resendOtp(userId, method);
    }

    public void forgotPassword(String email) {
        User user = usersService.findByEmail(email);
        if (user == null) return; // silently succeed

        String raw = generateSecureToken();
        Instant expires = Instant.now().plusSeconds(600); // 10 minutes
        usersService.setResetToken(user.getId(), sha256(raw), expires);
        mailService.sendPasswordReset(user, raw);
        log.info("Password reset sent to {}", email);
    }

    public void resetPassword(String rawToken, String newPassword) {
        User user = usersService.findByResetToken(rawToken);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired");
        }
        usersService.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
        log.info("Password reset for {}", user.getEmail());
    }

    public LoginResponse buildFullResponse(User user) {
        String token = jwtService.generateToken(
                user.getId().toString(), user.getEmail(), user.getRole().name());
        return LoginResponse.builder()
                .accessToken(token)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .name(user.getName())
                        .role(user.getRole().name())
                        .build())
                .build();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
