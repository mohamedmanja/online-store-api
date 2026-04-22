package com.harmony.store.users;

import com.harmony.store.users.dto.AddressDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressesService {

    private final AddressRepository addressRepo;
    private final UserRepository userRepo;

    public List<Address> findByUser(UUID userId) {
        return addressRepo.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public Address create(UUID userId, AddressDto dto) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Address address = Address.builder()
                .user(user)
                .name(dto.getName())
                .line1(dto.getLine1())
                .line2(dto.getLine2())
                .city(dto.getCity())
                .state(dto.getState())
                .postalCode(dto.getPostalCode())
                .country(dto.getCountry())
                .build();
        return addressRepo.save(address);
    }

    public Address update(UUID userId, UUID addressId, AddressDto dto) {
        Address address = addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        if (dto.getName() != null) address.setName(dto.getName());
        if (dto.getLine1() != null) address.setLine1(dto.getLine1());
        address.setLine2(dto.getLine2());
        if (dto.getCity() != null) address.setCity(dto.getCity());
        if (dto.getState() != null) address.setState(dto.getState());
        if (dto.getPostalCode() != null) address.setPostalCode(dto.getPostalCode());
        if (dto.getCountry() != null) address.setCountry(dto.getCountry());
        return addressRepo.save(address);
    }

    public void delete(UUID userId, UUID addressId) {
        Address address = addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        addressRepo.delete(address);
    }
}
