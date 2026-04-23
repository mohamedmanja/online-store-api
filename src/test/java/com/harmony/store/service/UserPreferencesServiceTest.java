package com.harmony.store.service;

import com.harmony.store.dto.UserPreferencesDto;
import com.harmony.store.model.User;
import com.harmony.store.model.UserPreferences;
import com.harmony.store.model.UserRole;
import com.harmony.store.repository.UserPreferencesRepository;
import com.harmony.store.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock UserPreferencesRepository prefsRepo;
    @Mock UserRepository            userRepo;

    @InjectMocks UserPreferencesService userPreferencesService;

    private User buildUser(UUID id) {
        return User.builder().id(id).email("user@example.com").role(UserRole.customer).build();
    }

    // ── getOrCreate ───────────────────────────────────────────────────────────

    @Test
    void getOrCreate_existingPrefs_returnsWithoutSaving() {
        UUID userId = UUID.randomUUID();
        UserPreferences prefs = UserPreferences.builder().build();
        when(prefsRepo.findByUserId(userId)).thenReturn(Optional.of(prefs));

        UserPreferences result = userPreferencesService.getOrCreate(userId);

        assertThat(result).isEqualTo(prefs);
        verify(prefsRepo, never()).save(any());
        verifyNoInteractions(userRepo);
    }

    @Test
    void getOrCreate_noExistingPrefs_createsAndSaves() {
        UUID userId = UUID.randomUUID();
        User user   = buildUser(userId);
        UserPreferences saved = UserPreferences.builder().user(user).build();

        when(prefsRepo.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(prefsRepo.save(any(UserPreferences.class))).thenReturn(saved);

        UserPreferences result = userPreferencesService.getOrCreate(userId);

        assertThat(result).isEqualTo(saved);
        verify(prefsRepo).save(argThat(p -> user.equals(p.getUser())));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_setsShippingAndBillingAddressIds() {
        UUID userId          = UUID.randomUUID();
        UUID shippingAddrId  = UUID.randomUUID();
        UUID billingAddrId   = UUID.randomUUID();

        UserPreferences prefs = UserPreferences.builder().build();
        when(prefsRepo.findByUserId(userId)).thenReturn(Optional.of(prefs));
        when(prefsRepo.save(prefs)).thenReturn(prefs);

        UserPreferencesDto dto = new UserPreferencesDto();
        dto.setDefaultShippingAddressId(shippingAddrId);
        dto.setDefaultBillingAddressId(billingAddrId);

        UserPreferences result = userPreferencesService.update(userId, dto);

        assertThat(result.getDefaultShippingAddressId()).isEqualTo(shippingAddrId);
        assertThat(result.getDefaultBillingAddressId()).isEqualTo(billingAddrId);
        verify(prefsRepo).save(prefs);
    }

    @Test
    void update_nullAddressIds_clearsExistingValues() {
        UUID userId = UUID.randomUUID();
        UserPreferences prefs = UserPreferences.builder()
                .defaultShippingAddressId(UUID.randomUUID())
                .defaultBillingAddressId(UUID.randomUUID())
                .build();

        when(prefsRepo.findByUserId(userId)).thenReturn(Optional.of(prefs));
        when(prefsRepo.save(prefs)).thenReturn(prefs);

        UserPreferencesDto dto = new UserPreferencesDto(); // both null

        userPreferencesService.update(userId, dto);

        assertThat(prefs.getDefaultShippingAddressId()).isNull();
        assertThat(prefs.getDefaultBillingAddressId()).isNull();
    }
}
