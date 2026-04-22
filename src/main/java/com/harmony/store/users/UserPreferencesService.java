package com.harmony.store.users;

import com.harmony.store.users.dto.UserPreferencesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
