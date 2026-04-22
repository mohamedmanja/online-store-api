package com.harmony.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.harmony.store.model.Address;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByUserIdOrderByCreatedAtAsc(UUID userId);
    Optional<Address> findByIdAndUserId(UUID id, UUID userId);
}
