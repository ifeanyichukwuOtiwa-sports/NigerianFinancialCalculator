package iwo.wintech.ngnfincalc.auth.security;

import iwo.wintech.ngnfincalc.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduction: users created before the created_at fix have NULL created_at / updated_at in the DB.
 * Verify the real authentication path still works for such legacy rows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Legacy user (NULL timestamps) login")
class LegacyUserLoginReproTest {

    @Autowired
    private BrandAuthenticationProvider authenticationProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("authenticate succeeds for a row with NULL created_at and NULL updated_at")
    void authenticatesLegacyRowWithNullTimestamps() {
        String email = "legacy@test.com";
        String rawPassword = "secret123";

        // Simulate a pre-fix row: created_at + updated_at explicitly NULL.
        jdbcClient.sql("""
                INSERT INTO users (brand, email, password_hash, full_name, created_at, updated_at)
                VALUES (:brand, :email, :passwordHash, :fullName, NULL, NULL)
                """)
                .param("brand", "NGN")
                .param("email", email)
                .param("passwordHash", passwordEncoder.encode(rawPassword))
                .param("fullName", "Legacy User")
                .update();

        Authentication result = authenticationProvider.authenticate(
                new BrandAuthentication("NGN", email, rawPassword));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result).isInstanceOf(BrandAuthentication.class);
        assertThat(((BrandAuthentication) result).getEmail()).isEqualTo(email);
    }
}
