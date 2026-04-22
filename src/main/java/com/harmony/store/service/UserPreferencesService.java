package com.harmony.store.service;

import com.harmony.store.dto.UserPreferencesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import com.harmony.store.model.User;
import com.harmony.store.model.UserPreferences;
import com.harmony.store.repository.UserPreferencesRepository;
import com.harmony.store.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserPreferencesService {

    private final UserPreferencesRepository prefsRepo;
    private final UserRepository userRepo;

    public UserPreferences getOrCreate(UUID userId) {
        return prefsRepo.findByUserId(userId).orElseGet(() -> {
            User user = userRepo.findById(userId).orElseThrow();
            return prefsRepo.save(UserPreferences.builder().user(user).build());
        });
    }

    public UserPreferences update(UUID userId, UserPreferencesDto dto) {
        UserPreferences prefs = getOrCreate(userId);
        prefs.setDefaultShippingAddressId(dto.getDefaultShippingAddressId());
        prefs.setDefaultBillingAddressId(dto.getDefaultBillingAddressId());
        return prefsRepo.save(prefs);
    }
}
