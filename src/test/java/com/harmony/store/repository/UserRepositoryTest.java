package com.harmony.store.repository;

import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:userrepotest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class UserRepositoryTest {

    @Autowired
    UserRepository repo;

    private User savedUser(String email) {
        return repo.save(User.builder()
                .email(email)
                .name("Test User")
                .role(UserRole.customer)
                .build());
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    void findByEmail_existingEmail_returnsUser() {
        savedUser("alice@example.com");

        Optional<User> result = repo.findByEmail("alice@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByEmail_unknownEmail_returnsEmpty() {
        Optional<User> result = repo.findByEmail("nobody@example.com");
        assertThat(result).isEmpty();
    }

    // ── existsByEmail ─────────────────────────────────────────────────────────

    @Test
    void existsByEmail_existingEmail_returnsTrue() {
        savedUser("bob@example.com");
        assertThat(repo.existsByEmail("bob@example.com")).isTrue();
    }

    @Test
    void existsByEmail_unknownEmail_returnsFalse() {
        assertThat(repo.existsByEmail("nobody@example.com")).isFalse();
    }

    // ── findByPasswordResetToken ──────────────────────────────────────────────

    @Test
    void findByPasswordResetToken_matchingToken_returnsUser() {
        User user = User.builder()
                .email("carol@example.com")
                .name("Carol")
                .role(UserRole.customer)
                .passwordResetToken("reset-abc-123")
                .build();
        repo.save(user);

        Optional<User> result = repo.findByPasswordResetToken("reset-abc-123");
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("carol@example.com");
    }

    @Test
    void findByPasswordResetToken_unknownToken_returnsEmpty() {
        assertThat(repo.findByPasswordResetToken("bad-token")).isEmpty();
    }

    // ── findByProviderAndProviderId ───────────────────────────────────────────

    @Test
    void findByProviderAndProviderId_existingOAuthUser_returnsUser() {
        User user = User.builder()
                .email("dave@gmail.com")
                .name("Dave")
                .role(UserRole.customer)
                .provider("google")
                .providerId("google-uid-42")
                .build();
        repo.save(user);

        Optional<User> result = repo.findByProviderAndProviderId("google", "google-uid-42");
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("dave@gmail.com");
    }

    @Test
    void findByProviderAndProviderId_unknownProvider_returnsEmpty() {
        assertThat(repo.findByProviderAndProviderId("github", "some-id")).isEmpty();
    }
}
