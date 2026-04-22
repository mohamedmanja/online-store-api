package com.harmony.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;
import com.harmony.store.model.TwoFactorConfig;

public interface TwoFactorConfigRepository extends JpaRepository<TwoFactorConfig, UUID> {
    Optional<TwoFactorConfig> findByUserId(UUID userId);

    // Loads the record including the normally-ignored sensitive columns
    @Query("SELECT c FROM TwoFactorConfig c WHERE c.user.id = :userId")
    Optional<TwoFactorConfig> findByUserIdWithSecrets(UUID userId);
}
