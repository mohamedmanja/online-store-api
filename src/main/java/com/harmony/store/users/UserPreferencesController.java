package com.harmony.store.users;

import com.harmony.store.config.UserPrincipal;
import com.harmony.store.users.dto.UserPreferencesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/user-preferences")
@RequiredArgsConstructor
public class UserPreferencesController {

    private final UserPreferencesService service;

    @GetMapping
    public UserPreferences get(@AuthenticationPrincipal UserPrincipal principal) {
        return service.getOrCreate(UUID.fromString(principal.getId()));
    }

    @PutMapping
    public UserPreferences update(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestBody UserPreferencesDto dto) {
        return service.update(UUID.fromString(principal.getId()), dto);
    }
}
