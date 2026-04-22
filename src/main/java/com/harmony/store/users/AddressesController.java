package com.harmony.store.users;

import com.harmony.store.config.UserPrincipal;
import com.harmony.store.users.dto.AddressDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
public class AddressesController {

    private final AddressesService addressesService;

    @GetMapping
    public List<Address> list(@AuthenticationPrincipal UserPrincipal principal) {
        return addressesService.findByUser(UUID.fromString(principal.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Address create(@AuthenticationPrincipal UserPrincipal principal,
                          @Valid @RequestBody AddressDto dto) {
        return addressesService.create(UUID.fromString(principal.getId()), dto);
    }

    @PutMapping("/{id}")
    public Address update(@AuthenticationPrincipal UserPrincipal principal,
                          @PathVariable UUID id,
                          @Valid @RequestBody AddressDto dto) {
        return addressesService.update(UUID.fromString(principal.getId()), id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID id) {
        addressesService.delete(UUID.fromString(principal.getId()), id);
    }
}
