package com.harmony.store.service;

import com.harmony.store.model.TwoFactorConfig;
import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import com.harmony.store.repository.TwoFactorConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    @Mock TwoFactorConfigRepository tfaRepo;
    @Mock UsersService              usersService;
    @Mock MailService               mailService;

    @InjectMocks TwoFactorService twoFactorService;

    private User buildUser(UUID id) {
        return User.builder().id(id).email("user@example.com").role(UserRole.customer).build();
    }

    private TwoFactorConfig emptyConfig(UUID userId) {
        User user = buildUser(userId);
        return TwoFactorConfig.builder().user(user).build();
    }

    // ── getStatus ─────────────────────────────────────────────────────────────

    @Test
    void getStatus_existingConfig_returnsCorrectStatus() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setEmailEnabled(true);
        cfg.setDefaultMethod("email");

        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        TwoFactorService.TwoFactorStatus status = twoFactorService.getStatus(userId);

        assertThat(status.anyEnabled()).isTrue();
        assertThat(status.emailEnabled()).isTrue();
        assertThat(status.totpEnabled()).isFalse();
        assertThat(status.defaultMethod()).isEqualTo("email");
        assertThat(status.enabledMethods()).containsExactly("email");
    }

    @Test
    void getStatus_noMethodsEnabled_anyEnabledIsFalse() {
        UUID userId = UUID.randomUUID();
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(emptyConfig(userId)));

        TwoFactorService.TwoFactorStatus status = twoFactorService.getStatus(userId);

        assertThat(status.anyEnabled()).isFalse();
        assertThat(status.enabledMethods()).isEmpty();
        assertThat(status.defaultMethod()).isNull();
    }

    @Test
    void getStatus_noExistingConfig_createsNewOne() {
        UUID userId = UUID.randomUUID();
        User user   = buildUser(userId);
        TwoFactorConfig newCfg = emptyConfig(userId);

        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.empty());
        when(usersService.findById(userId)).thenReturn(user);
        when(tfaRepo.save(any(TwoFactorConfig.class))).thenReturn(newCfg);

        TwoFactorService.TwoFactorStatus status = twoFactorService.getStatus(userId);

        assertThat(status.anyEnabled()).isFalse();
        verify(tfaRepo).save(any(TwoFactorConfig.class));
    }

    @Test
    void getStatus_phoneNumber_isMasked() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setPhoneEnabled(true);
        cfg.setPhoneNumber("5551234567");

        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        TwoFactorService.TwoFactorStatus status = twoFactorService.getStatus(userId);

        assertThat(status.phoneNumber()).doesNotContain("5551");
        assertThat(status.phoneNumber()).endsWith("4567");
    }

    // ── enableEmail ───────────────────────────────────────────────────────────

    @Test
    void enableEmail_setsEmailEnabledAndSaves() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        twoFactorService.enableEmail(userId);

        assertThat(cfg.isEmailEnabled()).isTrue();
        assertThat(cfg.getDefaultMethod()).isEqualTo("email");
        verify(tfaRepo).save(cfg);
    }

    @Test
    void enableEmail_doesNotOverrideExistingDefaultMethod() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setTotpEnabled(true);
        cfg.setDefaultMethod("totp");
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        twoFactorService.enableEmail(userId);

        assertThat(cfg.getDefaultMethod()).isEqualTo("totp"); // not overwritten
    }

    // ── disableEmail ──────────────────────────────────────────────────────────

    @Test
    void disableEmail_clearsEmailEnabledAndSaves() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setEmailEnabled(true);
        cfg.setDefaultMethod("email");
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        twoFactorService.disableEmail(userId);

        assertThat(cfg.isEmailEnabled()).isFalse();
        assertThat(cfg.getDefaultMethod()).isNull();
        verify(tfaRepo).save(cfg);
    }

    @Test
    void disableEmail_fallsBackToOtherEnabledMethod() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setEmailEnabled(true);
        cfg.setTotpEnabled(true);
        cfg.setDefaultMethod("email");
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        twoFactorService.disableEmail(userId);

        assertThat(cfg.getDefaultMethod()).isEqualTo("totp");
    }

    // ── disableTotp ───────────────────────────────────────────────────────────

    @Test
    void disableTotp_clearsTotpAndUpdatesDefault() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setTotpEnabled(true);
        cfg.setEmailEnabled(true);
        cfg.setDefaultMethod("totp");
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        twoFactorService.disableTotp(userId);

        assertThat(cfg.isTotpEnabled()).isFalse();
        assertThat(cfg.getDefaultMethod()).isEqualTo("email");
        verify(tfaRepo).save(cfg);
    }

    // ── disablePhone ──────────────────────────────────────────────────────────

    @Test
    void disablePhone_clearsPhoneAndNumber() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setPhoneEnabled(true);
        cfg.setPhoneNumber("+15551234567");
        cfg.setDefaultMethod("phone");
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        twoFactorService.disablePhone(userId);

        assertThat(cfg.isPhoneEnabled()).isFalse();
        assertThat(cfg.getPhoneNumber()).isNull();
        assertThat(cfg.getDefaultMethod()).isNull();
        verify(tfaRepo).save(cfg);
    }

    // ── setDefaultMethod ──────────────────────────────────────────────────────

    @Test
    void setDefaultMethod_validEnabledMethod_setsItAndSaves() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setEmailEnabled(true);
        cfg.setTotpEnabled(true);
        cfg.setDefaultMethod("email");
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        twoFactorService.setDefaultMethod(userId, "totp");

        assertThat(cfg.getDefaultMethod()).isEqualTo("totp");
        verify(tfaRepo).save(cfg);
    }

    @Test
    void setDefaultMethod_methodNotEnabled_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId); // no methods enabled
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        assertThatThrownBy(() -> twoFactorService.setDefaultMethod(userId, "totp"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(tfaRepo, never()).save(any());
    }

    // ── verifyLogin ───────────────────────────────────────────────────────────

    @Test
    void verifyLogin_totpNotEnabled_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setTotpEnabled(false);

        when(tfaRepo.findByUserIdWithSecrets(userId)).thenReturn(Optional.of(cfg));

        assertThatThrownBy(() -> twoFactorService.verifyLogin(userId, "123456", "totp"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void verifyLogin_expiredOtp_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setPendingOtpHash("somehash");
        cfg.setPendingOtpExpires(Instant.now().minusSeconds(60)); // expired

        when(tfaRepo.findByUserIdWithSecrets(userId)).thenReturn(Optional.of(cfg));

        assertThatThrownBy(() -> twoFactorService.verifyLogin(userId, "123456", "email"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void verifyLogin_invalidOtpCode_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        // Correct hash would be sha256("999999"), but we pass "111111"
        String correctHash = sha256("999999");
        cfg.setPendingOtpHash(correctHash);
        cfg.setPendingOtpExpires(Instant.now().plusSeconds(300));

        when(tfaRepo.findByUserIdWithSecrets(userId)).thenReturn(Optional.of(cfg));

        assertThatThrownBy(() -> twoFactorService.verifyLogin(userId, "111111", "email"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verifyLogin_correctOtp_clearsHashAndSaves() {
        UUID userId = UUID.randomUUID();
        TwoFactorConfig cfg = emptyConfig(userId);
        String otp = "123456";
        cfg.setPendingOtpHash(sha256(otp));
        cfg.setPendingOtpExpires(Instant.now().plusSeconds(300));

        when(tfaRepo.findByUserIdWithSecrets(userId)).thenReturn(Optional.of(cfg));

        twoFactorService.verifyLogin(userId, otp, "email");

        assertThat(cfg.getPendingOtpHash()).isNull();
        assertThat(cfg.getPendingOtpExpires()).isNull();
        verify(tfaRepo, atLeastOnce()).save(cfg);
    }

    // ── resendOtp ─────────────────────────────────────────────────────────────

    @Test
    void resendOtp_email_sendsMailAndReturnsMessage() {
        UUID userId = UUID.randomUUID();
        User user   = buildUser(userId);
        TwoFactorConfig cfg = emptyConfig(userId);

        when(usersService.findById(userId)).thenReturn(user);
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        String result = twoFactorService.resendOtp(userId, "email");

        assertThat(result).contains(user.getEmail());
        verify(mailService).sendTwoFactorOtp(eq(user), anyString());
        verify(tfaRepo, atLeastOnce()).save(cfg);
    }

    @Test
    void resendOtp_phoneNoNumberConfigured_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        User user   = buildUser(userId);
        TwoFactorConfig cfg = emptyConfig(userId); // no phone number

        when(usersService.findById(userId)).thenReturn(user);
        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        assertThatThrownBy(() -> twoFactorService.resendOtp(userId, "phone"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── initiate2FALogin ──────────────────────────────────────────────────────

    @Test
    void initiate2FALogin_emailDefault_sendsOtpAndReturnsHint() {
        UUID userId = UUID.randomUUID();
        User user   = buildUser(userId);
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setEmailEnabled(true);
        cfg.setDefaultMethod("email");

        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        TwoFactorService.InitiatedLogin result = twoFactorService.initiate2FALogin(user);

        assertThat(result.defaultMethod()).isEqualTo("email");
        assertThat(result.methods()).containsExactly("email");
        assertThat(result.hint()).contains(user.getEmail());
        verify(mailService).sendTwoFactorOtp(eq(user), anyString());
    }

    @Test
    void initiate2FALogin_totpDefault_doesNotSendEmail() {
        UUID userId = UUID.randomUUID();
        User user   = buildUser(userId);
        TwoFactorConfig cfg = emptyConfig(userId);
        cfg.setTotpEnabled(true);
        cfg.setDefaultMethod("totp");

        when(tfaRepo.findByUserId(userId)).thenReturn(Optional.of(cfg));

        TwoFactorService.InitiatedLogin result = twoFactorService.initiate2FALogin(user);

        assertThat(result.defaultMethod()).isEqualTo("totp");
        assertThat(result.hint()).contains("authenticator");
        verifyNoInteractions(mailService);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
