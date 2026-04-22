package com.harmony.store.auth;

import com.harmony.store.mail.MailService;
import com.harmony.store.users.TwoFactorConfig;
import com.harmony.store.users.TwoFactorConfigRepository;
import com.harmony.store.users.User;
import com.harmony.store.users.UsersService;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final String STORE_NAME = "Harmony";

    private final TwoFactorConfigRepository tfaRepo;
    private final UsersService usersService;
    private final MailService  mailService;

    // ── Internal helpers ──────────────────────────────────────────────────────

    private TwoFactorConfig getOrCreate(UUID userId) {
        return tfaRepo.findByUserId(userId).orElseGet(() -> {
            User user = usersService.findById(userId);
            return tfaRepo.save(TwoFactorConfig.builder().user(user).build());
        });
    }

    private TwoFactorConfig withSecrets(UUID userId) {
        return tfaRepo.findByUserIdWithSecrets(userId)
                .orElseGet(() -> getOrCreate(userId));
    }

    private List<String> enabledMethods(TwoFactorConfig cfg) {
        List<String> methods = new ArrayList<>();
        if (cfg.isTotpEnabled())  methods.add("totp");
        if (cfg.isEmailEnabled()) methods.add("email");
        if (cfg.isPhoneEnabled()) methods.add("phone");
        return methods;
    }

    private String generateOtp() {
        return String.format("%06d", new Random().nextInt(1_000_000));
    }

    private String hashOtp(String otp) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(otp.getBytes()));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void assertOtp(String code, TwoFactorConfig cfg) {
        if (cfg.getPendingOtpHash() == null || cfg.getPendingOtpExpires() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending OTP — request a new one");
        }
        if (Instant.now().isAfter(cfg.getPendingOtpExpires())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired");
        }
        if (!hashOtp(code).equals(cfg.getPendingOtpHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
        }
    }

    private void saveOtp(TwoFactorConfig cfg, String otp) {
        cfg.setPendingOtpHash(hashOtp(otp));
        cfg.setPendingOtpExpires(Instant.now().plusSeconds(600));
        tfaRepo.save(cfg);
    }

    private void clearOtp(TwoFactorConfig cfg) {
        cfg.setPendingOtpHash(null);
        cfg.setPendingOtpExpires(null);
        tfaRepo.save(cfg);
    }

    private String maskPhone(String phone) {
        return phone.substring(0, phone.length() - 4).replaceAll("\\d", "*") + phone.substring(phone.length() - 4);
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public TwoFactorStatus getStatus(UUID userId) {
        TwoFactorConfig cfg = getOrCreate(userId);
        List<String> methods = enabledMethods(cfg);
        String defaultMethod = cfg.getDefaultMethod() != null ? cfg.getDefaultMethod()
                : methods.isEmpty() ? null : methods.get(0);
        return new TwoFactorStatus(
                cfg.isTotpEnabled(), cfg.isEmailEnabled(), cfg.isPhoneEnabled(),
                cfg.getPhoneNumber() != null ? maskPhone(cfg.getPhoneNumber()) : null,
                methods, defaultMethod, !methods.isEmpty()
        );
    }

    public record TwoFactorStatus(boolean totpEnabled, boolean emailEnabled, boolean phoneEnabled,
                                   String phoneNumber, List<String> enabledMethods,
                                   String defaultMethod, boolean anyEnabled) {}

    // ── TOTP ──────────────────────────────────────────────────────────────────

    public Map<String, String> setupTotp(UUID userId) {
        User user = usersService.findById(userId);
        String secret = new DefaultSecretGenerator().generate();

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(STORE_NAME)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6).period(30)
                .build();

        String qrCode;
        try {
            ZxingPngQrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageBytes = generator.generate(qrData);
            qrCode = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (QrGenerationException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "QR code generation failed");
        }

        TwoFactorConfig cfg = getOrCreate(userId);
        cfg.setTotpSecret(secret);
        tfaRepo.save(cfg);

        return Map.of("secret", secret, "qrCode", qrCode);
    }

    public void enableTotp(UUID userId, String code) {
        TwoFactorConfig cfg = withSecrets(userId);
        if (cfg.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Run TOTP setup first");
        }
        if (!verifyTotpCode(cfg.getTotpSecret(), code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid TOTP code");
        }
        cfg.setTotpEnabled(true);
        if (cfg.getDefaultMethod() == null) cfg.setDefaultMethod("totp");
        tfaRepo.save(cfg);
    }

    public void disableTotp(UUID userId) {
        TwoFactorConfig cfg = getOrCreate(userId);
        cfg.setTotpEnabled(false);
        if ("totp".equals(cfg.getDefaultMethod())) {
            List<String> rest = enabledMethods(cfg).stream().filter(m -> !"totp".equals(m)).toList();
            cfg.setDefaultMethod(rest.isEmpty() ? null : rest.get(0));
        }
        tfaRepo.save(cfg);
    }

    // ── Email OTP ─────────────────────────────────────────────────────────────

    public void enableEmail(UUID userId) {
        TwoFactorConfig cfg = getOrCreate(userId);
        cfg.setEmailEnabled(true);
        if (cfg.getDefaultMethod() == null) cfg.setDefaultMethod("email");
        tfaRepo.save(cfg);
    }

    public void disableEmail(UUID userId) {
        TwoFactorConfig cfg = getOrCreate(userId);
        cfg.setEmailEnabled(false);
        if ("email".equals(cfg.getDefaultMethod())) {
            List<String> rest = enabledMethods(cfg).stream().filter(m -> !"email".equals(m)).toList();
            cfg.setDefaultMethod(rest.isEmpty() ? null : rest.get(0));
        }
        tfaRepo.save(cfg);
    }

    // ── Phone OTP ─────────────────────────────────────────────────────────────

    public void setupPhone(UUID userId, String phoneNumber) {
        TwoFactorConfig cfg = getOrCreate(userId);
        String otp = generateOtp();
        cfg.setPhoneNumber(phoneNumber);
        saveOtp(cfg, otp);
        sendSms(phoneNumber, "Your " + STORE_NAME + " verification code: " + otp);
    }

    public void verifyPhoneSetup(UUID userId, String code) {
        TwoFactorConfig cfg = withSecrets(userId);
        assertOtp(code, cfg);
        cfg.setPhoneEnabled(true);
        if (cfg.getDefaultMethod() == null) cfg.setDefaultMethod("phone");
        clearOtp(cfg);
        tfaRepo.save(cfg);
    }

    public void disablePhone(UUID userId) {
        TwoFactorConfig cfg = getOrCreate(userId);
        cfg.setPhoneEnabled(false);
        cfg.setPhoneNumber(null);
        if ("phone".equals(cfg.getDefaultMethod())) {
            List<String> rest = enabledMethods(cfg).stream().filter(m -> !"phone".equals(m)).toList();
            cfg.setDefaultMethod(rest.isEmpty() ? null : rest.get(0));
        }
        tfaRepo.save(cfg);
    }

    // ── Default method ────────────────────────────────────────────────────────

    public void setDefaultMethod(UUID userId, String method) {
        TwoFactorConfig cfg = getOrCreate(userId);
        if (!enabledMethods(cfg).contains(method)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Method '" + method + "' is not enabled");
        }
        cfg.setDefaultMethod(method);
        tfaRepo.save(cfg);
    }

    // ── Login flow ────────────────────────────────────────────────────────────

    public record InitiatedLogin(List<String> methods, String defaultMethod, String hint) {}

    public InitiatedLogin initiate2FALogin(User user) {
        TwoFactorConfig cfg = getOrCreate(user.getId());
        List<String> methods = enabledMethods(cfg);
        String defaultMethod = cfg.getDefaultMethod() != null ? cfg.getDefaultMethod() : methods.get(0);

        if ("email".equals(defaultMethod)) {
            String otp = generateOtp();
            saveOtp(cfg, otp);
            mailService.sendTwoFactorOtp(user, otp);
        } else if ("phone".equals(defaultMethod) && cfg.getPhoneNumber() != null) {
            String otp = generateOtp();
            saveOtp(cfg, otp);
            sendSms(cfg.getPhoneNumber(), "Your " + STORE_NAME + " login code: " + otp);
        }

        String hint = switch (defaultMethod) {
            case "totp"  -> "Open your authenticator app";
            case "email" -> "Code sent to " + user.getEmail();
            default      -> cfg.getPhoneNumber() != null ? "Code sent to " + maskPhone(cfg.getPhoneNumber()) : "Code sent";
        };

        return new InitiatedLogin(methods, defaultMethod, hint);
    }

    public String resendOtp(UUID userId, String method) {
        User user = usersService.findById(userId);
        TwoFactorConfig cfg = getOrCreate(userId);
        String otp = generateOtp();
        saveOtp(cfg, otp);

        if ("email".equals(method)) {
            mailService.sendTwoFactorOtp(user, otp);
            return "Code resent to " + user.getEmail();
        } else {
            if (cfg.getPhoneNumber() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No phone number configured");
            }
            sendSms(cfg.getPhoneNumber(), "Your " + STORE_NAME + " login code: " + otp);
            return "Code resent to " + maskPhone(cfg.getPhoneNumber());
        }
    }

    public void verifyLogin(UUID userId, String code, String method) {
        TwoFactorConfig cfg = withSecrets(userId);
        if ("totp".equals(method)) {
            if (!cfg.isTotpEnabled() || cfg.getTotpSecret() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOTP not enabled");
            }
            if (!verifyTotpCode(cfg.getTotpSecret(), code)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticator code");
            }
        } else {
            assertOtp(code, cfg);
            clearOtp(cfg);
        }
    }

    private boolean verifyTotpCode(String secret, String code) {
        CodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(), new SystemTimeProvider());
        return verifier.isValidCode(secret, code);
    }

    private void sendSms(String to, String message) {
        log.info("[SMS → {}]: {}", to, message);
        // TODO: integrate Twilio / AWS SNS
    }
}
