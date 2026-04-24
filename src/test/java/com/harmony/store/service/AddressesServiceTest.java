package com.harmony.store.service;

import com.harmony.store.dto.AddressDto;
import com.harmony.store.model.Address;
import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import com.harmony.store.repository.AddressRepository;
import com.harmony.store.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressesServiceTest {

    @Mock AddressRepository addressRepo;
    @Mock UserRepository    userRepo;

    @InjectMocks AddressesService addressesService;

    private User buildUser(UUID id) {
        return User.builder().id(id).email("user@example.com").role(UserRole.customer).build();
    }

    private AddressDto buildDto() {
        AddressDto dto = new AddressDto();
        dto.setName("Home");
        dto.setLine1("123 Main St");
        dto.setLine2("Apt 4");
        dto.setCity("Austin");
        dto.setState("TX");
        dto.setPostalCode("78701");
        dto.setCountry("US");
        return dto;
    }

    private Address buildAddress(UUID id, UUID userId) {
        User user = buildUser(userId);
        return Address.builder()
                .id(id)
                .user(user)
                .name("Home")
                .line1("123 Main St")
                .city("Austin")
                .state("TX")
                .postalCode("78701")
                .country("US")
                .build();
    }

    // ── findByUser ────────────────────────────────────────────────────────────

    @Test
    void findByUser_returnsAddressList() {
        UUID userId = UUID.randomUUID();
        List<Address> addresses = List.of(buildAddress(UUID.randomUUID(), userId));
        when(addressRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(addresses);

        assertThat(addressesService.findByUser(userId)).isEqualTo(addresses);
    }

    @Test
    void findByUser_noAddresses_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(addressRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

        assertThat(addressesService.findByUser(userId)).isEmpty();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validUser_savesAndReturnsAddress() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        Address saved = buildAddress(UUID.randomUUID(), userId);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(addressRepo.save(any(Address.class))).thenReturn(saved);

        Address result = addressesService.create(userId, buildDto());

        assertThat(result).isEqualTo(saved);
        verify(addressRepo).save(argThat(a ->
                "Home".equals(a.getName()) &&
                "123 Main St".equals(a.getLine1()) &&
                "Austin".equals(a.getCity()) &&
                user.equals(a.getUser())));
    }

    @Test
    void create_userNotFound_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressesService.create(userId, buildDto()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(addressRepo, never()).save(any());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_existingAddress_updatesNonNullFields() {
        UUID userId    = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        Address address = buildAddress(addressId, userId);

        when(addressRepo.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));
        when(addressRepo.save(address)).thenReturn(address);

        AddressDto dto = new AddressDto();
        dto.setCity("Dallas");
        dto.setPostalCode("75201");
        // name, line1, state, country left null — should not be overwritten

        Address result = addressesService.update(userId, addressId, dto);

        assertThat(result.getCity()).isEqualTo("Dallas");
        assertThat(result.getPostalCode()).isEqualTo("75201");
        assertThat(result.getName()).isEqualTo("Home");   // unchanged
        assertThat(result.getLine1()).isEqualTo("123 Main St"); // unchanged
    }

    @Test
    void update_line2NullAlwaysOverwrites() {
        UUID userId    = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        Address address = buildAddress(addressId, userId);
        address.setLine2("Old line2");

        when(addressRepo.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));
        when(addressRepo.save(address)).thenReturn(address);

        AddressDto dto = new AddressDto(); // line2 is null in dto

        addressesService.update(userId, addressId, dto);

        assertThat(address.getLine2()).isNull(); // line2 is always set (even to null)
    }

    @Test
    void update_notFound_throwsNotFound() {
        UUID userId    = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        when(addressRepo.findByIdAndUserId(addressId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressesService.update(userId, addressId, new AddressDto()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingAddress_deletesIt() {
        UUID userId    = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        Address address = buildAddress(addressId, userId);

        when(addressRepo.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));

        addressesService.delete(userId, addressId);

        verify(addressRepo).delete(address);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        UUID userId    = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        when(addressRepo.findByIdAndUserId(addressId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressesService.delete(userId, addressId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(addressRepo, never()).delete(any());
    }
}
