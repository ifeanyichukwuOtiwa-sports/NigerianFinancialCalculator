package iwo.wintech.ngnfincalc.scenarios.repository;

import iwo.wintech.ngnfincalc.TestcontainersConfiguration;
import iwo.wintech.ngnfincalc.auth.model.User;
import iwo.wintech.ngnfincalc.auth.repository.UserRepository;
import iwo.wintech.ngnfincalc.scenarios.model.InvestmentScenario;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({TestcontainersConfiguration.class, InvestmentScenarioRepositoryIntegrationTest.FixedClockConfig.class})
@SpringBootTest
@DisplayName("InvestmentScenarioRepository persistence")
class InvestmentScenarioRepositoryIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-04T05:06:07.987654Z");
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
    private InvestmentScenarioRepository scenarioRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("save() on new scenario returns populated createdAt with microsecond precision")
    void saveNewScenarioPopulatesCreatedAt() {
        Long userId = newUser("NGN", "scenario-owner@test.com");

        InvestmentScenario saved = scenarioRepository.save(newScenario("NGN", userId));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt())
                .as("save() must return the persisted createdAt, not null")
                .isEqualTo(EXPECTED_CREATED_AT);
        assertThat(saved.createdAt().getNano())
                .as("microsecond precision survives the round-trip")
                .isEqualTo(987_654_000);
    }

    @Test
    @DisplayName("update scoped to brand: wrong brand changes nothing and is rejected")
    void updateScopedToBrand() {
        Long ngnUser = newUser("NGN", "ngn-owner@test.com");
        InvestmentScenario saved = scenarioRepository.save(newScenario("NGN", ngnUser));

        InvestmentScenario crossTenant = saved.toBuilder().brand("USD").name("Hacked").build();
        assertThatThrownBy(() -> scenarioRepository.save(crossTenant))
                .isInstanceOf(RequestException.class)
                .satisfies(e -> assertThat(((RequestException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        InvestmentScenario reloaded = scenarioRepository.findByBrandAndId("NGN", saved.id()).orElseThrow();
        assertThat(reloaded.brand()).isEqualTo("NGN");
        assertThat(reloaded.name()).as("cross-tenant update must not touch the row").isEqualTo("Base Scenario");
    }

    private Long newUser(String brand, String email) {
        return userRepository.save(User.builder()
                .brand(brand)
                .email(email)
                .passwordHash("hash")
                .fullName("Owner")
                .build()).id();
    }

    private static InvestmentScenario newScenario(String brand, Long userId) {
        return InvestmentScenario.builder()
                .brand(brand)
                .userId(userId)
                .name("Base Scenario")
                .principal(new BigDecimal("1000.0000"))
                .annualRate(new BigDecimal("10.0000"))
                .years(5)
                .monthlyContribution(new BigDecimal("100.0000"))
                .compoundingFrequency("monthly")
                .taxStrategy("none")
                .build();
    }
}
