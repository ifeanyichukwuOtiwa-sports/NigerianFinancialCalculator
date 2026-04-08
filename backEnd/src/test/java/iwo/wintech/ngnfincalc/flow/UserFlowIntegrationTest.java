package iwo.wintech.ngnfincalc.flow;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.testcontainers.utility.TestcontainersConfiguration;
import tools.jackson.databind.ObjectMapper;
import iwo.wintech.ngnfincalc.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
@DisplayName("Full User Lifecycle Integration Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserFlowIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("User Journey: Attempt Unauthorized -> Register -> Duplicate Register Error -> Login -> Auth Failure Error -> Create Scenarios (None, WHT, Progressive) -> List -> Delete -> Logout")
    void fullUserJourney() {
        String email = "user-" + System.currentTimeMillis() + "@test.com";
        new UserActionsBuilder(restTemplate, objectMapper)
                .withBrand("NGN")
                
                .step("1. Attempt to list scenarios without login (should fail with 403 Forbidden)")
                .listScenarios()
                .expectStatus(403)
                
                .step("2. Attempt to check /api/auth/me without login (should fail with 401 Unauthorized)")
                .checkMe()
                .expectStatus(401)

                
                .step("3. Register new user")
                .register(email, "secret123", "Test User")
                .expectStatus(200)
                .expectJson("{email: '" + email + "', fullName: 'Test User'}")
                
                .step("4. Attempt to register again with same email (should fail with 400 Bad Request)")
                .register(email, "secret123", "Test User")
                .expectStatus(400)
                .expectJson("{code: '" + ErrorCode.EMAIL_EXISTS.name() + "'}")
                
                .step("5. Login with registered user")
                .login(email, "secret123")
                .expectStatus(200)
                .expectJson("{email: '" + email + "'}")
                
                .step("6. Attempt login with wrong password (should fail with 401 Unauthorized)")
                .login(email, "wrong")
                .expectStatus(401)
                .expectJson("{code: '" + ErrorCode.AUTH_FAILED.name() + "'}")
                
                .step("7. Check current session status")
                .checkMe()
                .expectStatus(200)
                .expectJson("{email: '" + email + "'}")
                
                .step("8. Create initial investment scenario (No Tax Strategy)")
                .saveScenario("Standard Savings", "1000000", "12", "5", "20000", "monthly", "none", "0")
                .expectStatus(200)
                .expectJson("{name: 'Standard Savings', estimatedTax: 0}")
                
                .step("9. Create scenario with Withholding Tax (WHT)")
                .saveScenario("Dividends Fund", "5000000", "15", "10", "100000", "quarterly", "wht", "0")
                .expectStatus(200)
                .expectJson("{taxStrategy: 'wht'}")
                .verifyField("estimatedTax", node -> assertThat(node.numberValue().doubleValue()).isGreaterThan(0.0))
                
                .step("10. Create scenario with Progressive PIT Tax Strategy")
                .saveScenario("Wealth Plan", "10000000", "18", "15", "500000", "annually", "progressive", "5000000")
                .expectStatus(200)
                .expectJson("{taxStrategy: 'progressive'}")
                .verifyField("estimatedTax", node -> assertThat(node.numberValue().doubleValue()).isGreaterThan(0.0))
                
                .step("11. Export scenario to PDF")
                .exportPdf(null) // UserActionsBuilder should handle null by using lastScenarioId
                .expectStatus(200)

                .step("12. Export scenario to CSV")
                .exportCsv(null)
                .expectStatus(200)

                .step("13. List user scenarios and verify all are present")
                .listScenarios()
                .expectStatus(200)
                .expectArraySize(3)
                
                .step("12. Delete the last created scenario")
                .deleteLastScenario()
                .expectStatus(204)
                
                .step("13. Verify scenario list count decreased")
                .listScenarios()
                .expectStatus(200)
                .expectArraySize(2)
                
                .step("14. Logout")
                .logout()
                .expectStatus(200)
                
                .step("15. Verify access is blocked again after logout")
                .listScenarios()
                .expectStatus(403);
    }

    @Test
    @Order(2)
    @DisplayName("Security: Verify Rate Limiting for Auth Endpoints")
    void testRateLimiting() {
        String email = "rate-limit-" + System.currentTimeMillis() + "@test.com";
        // Use a different IP for this test to avoid bucket exhaustion from first test
        UserActionsBuilder builder = new UserActionsBuilder(restTemplate, objectMapper)
                .withBrand("NGN")
                .withIp("192.168.1.1");

        // Use more attempts since we increased the limit
        for (int i = 0; i < 20; i++) {
            builder.login(email, "wrong-password").expectStatus(401);
        }

        // 21st attempt should be rate limited
        builder.login(email, "wrong-password").expectStatus(429)
                .expectJson("{code: 'RATE_LIMIT_EXCEEDED'}");
    }
}
