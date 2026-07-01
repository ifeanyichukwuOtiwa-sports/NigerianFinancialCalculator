package iwo.wintech.ngnfincalc.auth.repository;

import iwo.wintech.ngnfincalc.TestcontainersConfiguration;
import iwo.wintech.ngnfincalc.auth.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
}
