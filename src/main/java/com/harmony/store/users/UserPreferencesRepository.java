package com.harmony.store.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
    Optional<UserPreferences> findByUserId(UUID userId);
}
