package com.harmony.store.service;

import com.harmony.store.dto.ChangePasswordDto;
import com.harmony.store.dto.UpdateUserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import com.harmony.store.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UsersService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public User findByEmail(String email) {
        return userRepo.findByEmail(email).orElse(null);
    }

    public User findById(UUID id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User create(String email, String name, String passwordHash) {
        if (userRepo.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        User user = User.builder()
                .email(email)
                .name(name)
                .passwordHash(passwordHash)
                .role(UserRole.customer)
                .build();
        return userRepo.save(user);
    }

    public User findOrCreateOAuthUser(String email, String name, String avatarUrl,
                                      String provider, String providerId) {
        return userRepo.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepo.findByEmail(email)
                        .map(existing -> {
                            existing.setProvider(provider);
                            existing.setProviderId(providerId);
                            if (existing.getAvatarUrl() == null) existing.setAvatarUrl(avatarUrl);
                            if (existing.getName() == null) existing.setName(name);
                            return userRepo.save(existing);
                        })
                        .orElseGet(() -> userRepo.save(User.builder()
                                .email(email)
                                .name(name)
                                .avatarUrl(avatarUrl)
                                .provider(provider)
                                .providerId(providerId)
                                .role(UserRole.customer)
                                .build())));
    }

    public User update(UUID id, UpdateUserDto dto) {
        User user = findById(id);
        if (dto.getName() != null) user.setName(dto.getName());
        if (dto.getAvatarUrl() != null) user.setAvatarUrl(dto.getAvatarUrl());
        return userRepo.save(user);
    }

    public void changePassword(UUID userId, ChangePasswordDto dto) {
        User user = findById(userId);
        if (user.getPasswordHash() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password change not available for social login accounts");
        }
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepo.save(user);
    }

    public void setResetToken(UUID userId, String tokenHash, Instant expires) {
        User user = findById(userId);
        user.setPasswordResetToken(tokenHash);
        user.setPasswordResetExpires(expires);
        userRepo.save(user);
    }

    public User findByResetToken(String rawToken) {
        String hash = sha256(rawToken);
        return userRepo.findByPasswordResetToken(hash)
                .filter(u -> u.getPasswordResetExpires() != null
                          && Instant.now().isBefore(u.getPasswordResetExpires()))
                .orElse(null);
    }

    public void updatePassword(UUID userId, String newPasswordHash) {
        User user = findById(userId);
        user.setPasswordHash(newPasswordHash);
        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);
        userRepo.save(user);
    }

    public List<User> findAll() {
        return userRepo.findAll();
    }

    public User updateRole(UUID id, UserRole role) {
        User user = findById(id);
        user.setRole(role);
        return userRepo.save(user);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
