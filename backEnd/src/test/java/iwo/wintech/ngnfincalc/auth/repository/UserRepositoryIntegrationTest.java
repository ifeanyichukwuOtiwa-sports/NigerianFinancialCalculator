package iwo.wintech.ngnfincalc.auth.repository;

import iwo.wintech.ngnfincalc.TestcontainersConfiguration;
import iwo.wintech.ngnfincalc.auth.model.User;
import iwo.wintech.ngnfincalc.shared.error.ErrorCode;
import iwo.wintech.ngnfincalc.shared.error.RequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({TestcontainersConfiguration.class, UserRepositoryIntegrationTest.FixedClockConfig.class})
@SpringBootTest
@DisplayName("UserRepository persistence")
class UserRepositoryIntegrationTest {

    // Fixed instant so createdAt is deterministic — no system clock in the assertion.
    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-02T03:04:05Z");
    private static final LocalDateTime EXPECTED_CREATED_AT =
            LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("save() on new user returns populated createdAt (regression: was null)")
    void saveNewUserPopulatesCreatedAt() {
        User saved = userRepository.save(User.builder()
                .brand("NGN")
                .email("created-at-return@test.com")
                .passwordHash("hash")
                .fullName("Created At Return")
                .build());

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt())
                .as("save() must return the persisted createdAt, not the null input value")
                .isNotNull()
                .isEqualTo(EXPECTED_CREATED_AT);
    }

    @Test
    @DisplayName("createdAt round-trips through findById")
    void createdAtRoundTripsThroughFindById() {
        User saved = userRepository.save(User.builder()
                .brand("NGN")
                .email("created-at-roundtrip@test.com")
                .passwordHash("hash")
                .fullName("Round Trip")
                .build());

        Optional<User> reloaded = userRepository.findById(saved.id());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().createdAt())
                .as("createdAt read back from DB must match the value save() returned")
                .isEqualTo(saved.createdAt())
                .isEqualTo(EXPECTED_CREATED_AT);
    }

    @Test
    @DisplayName("duplicate (brand,email) insert throws DuplicateKeyException (unique constraint)")
    void duplicateEmailThrowsDuplicateKey() {
        userRepository.save(newUser("NGN", "dup@test.com"));

        assertThatExceptionOfType(DuplicateKeyException.class)
                .as("UNIQUE(brand,email) must be the source of truth for duplicates")
                .isThrownBy(() -> userRepository.save(newUser("NGN", "dup@test.com")));
    }

    @Test
    @DisplayName("same email allowed under a different brand (tenant isolation)")
    void sameEmailDifferentBrandAllowed() {
        userRepository.save(newUser("NGN", "shared@test.com"));

        User other = userRepository.save(newUser("USD", "shared@test.com"));

        assertThat(other.id()).isNotNull();
    }

    @Test
    @DisplayName("findByBrandAndEmail is brand-scoped")
    void findByBrandAndEmailIsBrandScoped() {
        userRepository.save(newUser("NGN", "scoped@test.com"));

        assertThat(userRepository.findByBrandAndEmail("NGN", "scoped@test.com")).isPresent();
        assertThat(userRepository.findByBrandAndEmail("USD", "scoped@test.com")).isEmpty();
    }

    @Test
    @DisplayName("update scoped to brand: wrong brand changes nothing and throws USER_NOT_FOUND")
    void updateScopedToBrand() {
        User saved = userRepository.save(newUser("NGN", "update-scope@test.com"));

        User crossTenant = saved.toBuilder().brand("USD").fullName("Hacked").build();
        assertThatThrownBy(() -> userRepository.save(crossTenant))
                .isInstanceOf(RequestException.class)
                .satisfies(e -> assertThat(((RequestException) e).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));

        User reloaded = userRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.brand()).as("cross-tenant update must not touch the row").isEqualTo("NGN");
        assertThat(reloaded.fullName()).isEqualTo("Seed Name");
    }

    @Test
    @DisplayName("update within same brand persists fields; brand stays immutable")
    void updateWithinBrand() {
        User saved = userRepository.save(newUser("NGN", "update-ok@test.com"));

        userRepository.save(saved.toBuilder().fullName("Renamed").build());

        User reloaded = userRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.fullName()).isEqualTo("Renamed");
        assertThat(reloaded.brand()).isEqualTo("NGN");
    }

    private static User newUser(String brand, String email) {
        return User.builder()
                .brand(brand)
                .email(email)
                .passwordHash("hash")
                .fullName("Seed Name")
                .build();
    }
}
