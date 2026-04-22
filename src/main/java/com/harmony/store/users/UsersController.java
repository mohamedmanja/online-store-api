package com.harmony.store.users;

import com.harmony.store.config.UserPrincipal;
import com.harmony.store.users.dto.ChangePasswordDto;
import com.harmony.store.users.dto.UpdateUserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UsersController {

    private final UsersService usersService;

    @GetMapping("/me")
    public User getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return usersService.findById(UUID.fromString(principal.getId()));
    }

    @PutMapping("/me")
    public User updateMe(@AuthenticationPrincipal UserPrincipal principal,
                         @RequestBody UpdateUserDto dto) {
        return usersService.update(UUID.fromString(principal.getId()), dto);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                @Valid @RequestBody ChangePasswordDto dto) {
        usersService.changePassword(UUID.fromString(principal.getId()), dto);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        return usersService.findAll();
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public User updateRole(@PathVariable UUID id, @RequestBody RoleUpdateRequest req) {
        return usersService.updateRole(id, req.role());
    }

    record RoleUpdateRequest(UserRole role) {}
}
