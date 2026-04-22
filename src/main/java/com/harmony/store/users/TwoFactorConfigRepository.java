package com.harmony.store.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TwoFactorConfigRepository extends JpaRepository<TwoFactorConfig, UUID> {
    Optional<TwoFactorConfig> findByUserId(UUID userId);

    // Loads the record including the normally-ignored sensitive columns
    @Query("SELECT c FROM TwoFactorConfig c WHERE c.user.id = :userId")
    Optional<TwoFactorConfig> findByUserIdWithSecrets(UUID userId);
}
