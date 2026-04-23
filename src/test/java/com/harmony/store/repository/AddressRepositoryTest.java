package com.harmony.store.repository;

import com.harmony.store.model.Address;
import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:addressrepotest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class AddressRepositoryTest {

    @Autowired UserRepository userRepo;
    @Autowired AddressRepository addressRepo;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user      = userRepo.save(User.builder().email("alice@example.com").name("Alice").role(UserRole.customer).build());
        otherUser = userRepo.save(User.builder().email("bob@example.com").name("Bob").role(UserRole.customer).build());
    }

    private Address buildAddress(User owner, String city) {
        return Address.builder()
                .user(owner)
                .name("Home")
                .line1("123 Main St")
                .city(city)
                .state("IL")
                .postalCode("62701")
                .country("US")
                .build();
    }

    // ── findByUserIdOrderByCreatedAtAsc ───────────────────────────────────────

    @Test
    void findByUserIdOrderByCreatedAtAsc_returnsOnlyUsersAddresses() {
        addressRepo.save(buildAddress(user, "Springfield"));
        addressRepo.save(buildAddress(user, "Shelbyville"));
        addressRepo.save(buildAddress(otherUser, "Capital City")); // should not appear

        List<Address> results = addressRepo.findByUserIdOrderByCreatedAtAsc(user.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Address::getCity)
                .containsExactlyInAnyOrder("Springfield", "Shelbyville");
    }

    @Test
    void findByUserIdOrderByCreatedAtAsc_noAddresses_returnsEmpty() {
        List<Address> results = addressRepo.findByUserIdOrderByCreatedAtAsc(user.getId());
        assertThat(results).isEmpty();
    }

    // ── findByIdAndUserId ─────────────────────────────────────────────────────

    @Test
    void findByIdAndUserId_correctUser_returnsAddress() {
        Address saved = addressRepo.save(buildAddress(user, "Springfield"));

        Optional<Address> result = addressRepo.findByIdAndUserId(saved.getId(), user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getCity()).isEqualTo("Springfield");
    }

    @Test
    void findByIdAndUserId_wrongUser_returnsEmpty() {
        Address saved = addressRepo.save(buildAddress(user, "Springfield"));

        Optional<Address> result = addressRepo.findByIdAndUserId(saved.getId(), otherUser.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndUserId_unknownId_returnsEmpty() {
        Optional<Address> result = addressRepo.findByIdAndUserId(UUID.randomUUID(), user.getId());
        assertThat(result).isEmpty();
    }
}
