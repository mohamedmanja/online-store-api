package com.harmony.store.service;

import com.harmony.store.dto.ChangePasswordDto;
import com.harmony.store.dto.UpdateUserDto;
import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import com.harmony.store.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsersServiceTest {

    @Mock UserRepository  userRepo;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UsersService usersService;

    private User buildUser(UUID id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .name("Test User")
                .passwordHash("hashed")
                .role(UserRole.customer)
                .build();
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    void findByEmail_found_returnsUser() {
        User user = buildUser(UUID.randomUUID(), "alice@example.com");
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThat(usersService.findByEmail("alice@example.com")).isEqualTo(user);
    }

    @Test
    void findByEmail_notFound_returnsNull() {
        when(userRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThat(usersService.findByEmail("nobody@example.com")).isNull();
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_found_returnsUser() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "bob@example.com");
        when(userRepo.findById(id)).thenReturn(Optional.of(user));

        assertThat(usersService.findById(id)).isEqualTo(user);
    }

    @Test
    void findById_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usersService.findById(id))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_newEmail_savesAndReturnsUser() {
        when(userRepo.existsByEmail("carol@example.com")).thenReturn(false);
        User saved = buildUser(UUID.randomUUID(), "carol@example.com");
        when(userRepo.save(any(User.class))).thenReturn(saved);

        User result = usersService.create("carol@example.com", "Carol", "hash");

        assertThat(result).isEqualTo(saved);
        verify(userRepo).save(argThat(u ->
                "carol@example.com".equals(u.getEmail()) &&
                "Carol".equals(u.getName()) &&
                "hash".equals(u.getPasswordHash()) &&
                u.getRole() == UserRole.customer));
    }

    @Test
    void create_duplicateEmail_throwsConflict() {
        when(userRepo.existsByEmail("dave@example.com")).thenReturn(true);

        assertThatThrownBy(() -> usersService.create("dave@example.com", "Dave", "hash"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(userRepo, never()).save(any());
    }

    // ── findOrCreateOAuthUser ─────────────────────────────────────────────────

    @Test
    void findOrCreateOAuthUser_existingByProvider_returnsExisting() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "eve@example.com");
        when(userRepo.findByProviderAndProviderId("google", "google-123"))
                .thenReturn(Optional.of(user));

        User result = usersService.findOrCreateOAuthUser(
                "eve@example.com", "Eve", "https://avatar.url", "google", "google-123");

        assertThat(result).isEqualTo(user);
        verify(userRepo, never()).save(any());
    }

    @Test
    void findOrCreateOAuthUser_existingByEmail_linksProviderAndSaves() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "frank@example.com");
        existing.setProvider(null);

        when(userRepo.findByProviderAndProviderId("google", "google-456"))
                .thenReturn(Optional.empty());
        when(userRepo.findByEmail("frank@example.com")).thenReturn(Optional.of(existing));
        when(userRepo.save(existing)).thenReturn(existing);

        usersService.findOrCreateOAuthUser(
                "frank@example.com", "Frank", "https://avatar.url", "google", "google-456");

        assertThat(existing.getProvider()).isEqualTo("google");
        assertThat(existing.getProviderId()).isEqualTo("google-456");
        verify(userRepo).save(existing);
    }

    @Test
    void findOrCreateOAuthUser_newUser_createsAndSaves() {
        when(userRepo.findByProviderAndProviderId("facebook", "fb-789"))
                .thenReturn(Optional.empty());
        when(userRepo.findByEmail("grace@example.com")).thenReturn(Optional.empty());
        User saved = buildUser(UUID.randomUUID(), "grace@example.com");
        when(userRepo.save(any(User.class))).thenReturn(saved);

        User result = usersService.findOrCreateOAuthUser(
                "grace@example.com", "Grace", "https://pic.url", "facebook", "fb-789");

        assertThat(result).isEqualTo(saved);
        verify(userRepo).save(argThat(u ->
                "grace@example.com".equals(u.getEmail()) &&
                "facebook".equals(u.getProvider()) &&
                "fb-789".equals(u.getProviderId())));
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_correctCurrentPassword_updatesHash() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "henry@example.com");
        when(userRepo.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("old-pass");
        dto.setNewPassword("new-pass");

        usersService.changePassword(id, dto);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepo).save(user);
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "iris@example.com");
        when(userRepo.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("wrong");
        dto.setNewPassword("new-pass");

        assertThatThrownBy(() -> usersService.changePassword(id, dto))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(userRepo, never()).save(any());
    }

    @Test
    void changePassword_oauthUser_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .email("oauth@example.com")
                .role(UserRole.customer)
                .build(); // no passwordHash
        when(userRepo.findById(id)).thenReturn(Optional.of(user));

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("any");
        dto.setNewPassword("new");

        assertThatThrownBy(() -> usersService.changePassword(id, dto))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_partialFields_onlyUpdatesNonNullFields() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "jack@example.com");
        when(userRepo.findById(id)).thenReturn(Optional.of(user));
        when(userRepo.save(user)).thenReturn(user);

        UpdateUserDto dto = new UpdateUserDto();
        dto.setName("Jack Updated");
        // avatarUrl left null

        usersService.update(id, dto);

        assertThat(user.getName()).isEqualTo("Jack Updated");
        assertThat(user.getAvatarUrl()).isNull();
    }

    // ── findByResetToken ──────────────────────────────────────────────────────

    @Test
    void findByResetToken_expiredToken_returnsNull() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "kate@example.com");
        user.setPasswordResetExpires(Instant.now().minusSeconds(60)); // expired
        // The service hashes the raw token — we match any hash from repo
        when(userRepo.findByPasswordResetToken(anyString())).thenReturn(Optional.of(user));

        User result = usersService.findByResetToken("raw-token");

        assertThat(result).isNull();
    }

    @Test
    void findByResetToken_noMatchInDb_returnsNull() {
        when(userRepo.findByPasswordResetToken(anyString())).thenReturn(Optional.empty());

        assertThat(usersService.findByResetToken("unknown")).isNull();
    }
}
