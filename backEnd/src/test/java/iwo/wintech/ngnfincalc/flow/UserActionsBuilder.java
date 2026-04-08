package iwo.wintech.ngnfincalc.flow;

import iwo.wintech.ngnfincalc.auth.dto.LoginRequest;
import iwo.wintech.ngnfincalc.auth.dto.RegisterRequest;
import iwo.wintech.ngnfincalc.scenarios.dto.ScenarioRequest;
import iwo.wintech.ngnfincalc.scenarios.dto.ScenarioResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.core.JacksonException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class UserActionsBuilder {
    private final ObjectMapper objectMapper;
    private final TestRestTemplate restTemplate;
    private String brand;
    private String clientIp;
    private final List<String> cookies = new ArrayList<>();
    private ResponseEntity<String> lastResponse;
    @Getter private Long lastScenarioId;
    private static final String APP_BRAND_HEADER = "x-app-brand";

    public UserActionsBuilder(final TestRestTemplate restTemplate, final ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public UserActionsBuilder withBrand(final String brand) {
        this.brand = brand;
        return this;
    }

    public UserActionsBuilder withIp(final String ip) {
        this.clientIp = ip;
        return this;
    }

    public UserActionsBuilder step(String description) {
        log.info("[TEST STEP] {}", description);
        return this;
    }

    private HttpHeaders createHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(APP_BRAND_HEADER, brand);
        if (clientIp != null) {
            headers.set("X-Forwarded-For", clientIp);
        }
        if (!cookies.isEmpty()) {
            // Send captured cookies back
            for (final String cookie : cookies) {
                // We only need the name=value part for the Cookie header
                headers.add(HttpHeaders.COOKIE, cookie.split(";")[0]);
            }
        }
        return headers;
    }

    public UserActionsBuilder register(final String email, final String password, final String fullName) {
        final RegisterRequest request = new RegisterRequest(email, password, fullName);
        lastResponse = restTemplate.exchange(
                "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(request, createHeaders()),
                String.class
        );
        return this;
    }

    public UserActionsBuilder login(final String email, final String password) {
        final LoginRequest request = new LoginRequest(email, password);
        final HttpHeaders headers = createHeaders();
        // Do NOT send session cookie for login - we want a fresh authentication
        lastResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
        );

        if (lastResponse.getStatusCode().is2xxSuccessful()) {
            updateCookies();
        }
        return this;
    }

    private void updateCookies() {
        final List<String> setCookies = lastResponse.getHeaders().get("Set-Cookie");
        if (setCookies != null) {
            // Update our cookie store with new cookies
            for (final String newCookie : setCookies) {
                final String name = newCookie.split("=")[0];
                // Remove old cookie with same name
                cookies.removeIf(c -> c.startsWith(name + "="));
                cookies.add(newCookie);
            }
        }
    }

    public UserActionsBuilder checkMe() {
        lastResponse = restTemplate.exchange(
                "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(null, createHeaders()),
                String.class
        );
        return this;
    }

    public UserActionsBuilder logout() {
        lastResponse = restTemplate.exchange(
                "/api/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(null, createHeaders()),
                String.class
        );
        cookies.clear();
        return this;
    }

    public UserActionsBuilder saveScenario(final String name, final String principal, final String rate, final String years,
                                    final String contribution, final String freq, final String tax, final String income) {
        final ScenarioRequest request = new ScenarioRequest(
                brand, name, new BigDecimal(principal), new BigDecimal(rate),
                Integer.valueOf(years), new BigDecimal(contribution), freq, tax, new BigDecimal(income)
        );
        lastResponse = restTemplate.exchange("/api/scenarios", HttpMethod.POST, new HttpEntity<>(request, createHeaders()), String.class);

        if (lastResponse.getStatusCode().is2xxSuccessful()) {
            final ScenarioResponse resp = objectMapper.readValue(lastResponse.getBody(), ScenarioResponse.class);
            lastScenarioId = resp.id();
        }
        return this;
    }

    public UserActionsBuilder listScenarios() {
        lastResponse = restTemplate.exchange(
                "/api/scenarios",
                HttpMethod.GET,
                new HttpEntity<>(null, createHeaders()),
                String.class
        );
        return this;
    }

    public UserActionsBuilder deleteLastScenario() {
        lastResponse = restTemplate.exchange(
                "/api/scenarios/" + lastScenarioId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, createHeaders()),
                String.class
        );
        return this;
    }

    public UserActionsBuilder exportPdf(final Long id) {
        final Long scenarioId = id != null ? id : lastScenarioId;
        lastResponse = restTemplate.exchange(
                "/api/scenarios/" + scenarioId + "/export/pdf",
                HttpMethod.GET,
                new HttpEntity<>(null, createHeaders()),
                String.class
        );
        return this;
    }

    public UserActionsBuilder exportCsv(final Long id) {
        final Long scenarioId = id != null ? id : lastScenarioId;
        lastResponse = restTemplate.exchange(
                "/api/scenarios/" + scenarioId + "/export/csv",
                HttpMethod.GET,
                new HttpEntity<>(null, createHeaders()),
                String.class
        );
        return this;
    }

    public UserActionsBuilder expectStatus(final int status) {
        assertThat(lastResponse.getStatusCode().value())
                .as("Expected status %d but was %d. Body: %s", status, lastResponse.getStatusCode().value(), lastResponse.getBody())
                .isEqualTo(status);
        return this;
    }

    public UserActionsBuilder expectJson(String expected) {
        return expectJson(expected, false);
    }

    public UserActionsBuilder expectJson(String expected, boolean strict) {
        try {
            JSONAssert.assertEquals(expected, lastResponse.getBody(), strict);
        } catch (JSONException e) {
            throw new AssertionError("Invalid expected JSON: " + expected, e);
        }
        return this;
    }

    public UserActionsBuilder expectArraySize(int size) {
        try {
            JsonNode root = objectMapper.readTree(lastResponse.getBody());
            assertThat(root.isArray()).as("Response body should be an array").isTrue();
            assertThat(root.size()).as("Array size should be %d", size).isEqualTo(size);
        } catch (JacksonException e) {
            throw new AssertionError("Failed to parse JSON for array size check", e);
        }
        return this;
    }

    public UserActionsBuilder verifyJson(Consumer<JsonNode> verifier) {
        try {
            JsonNode root = objectMapper.readTree(lastResponse.getBody());
            verifier.accept(root);
        } catch (JacksonException e) {
            throw new AssertionError("Failed to parse JSON for verification", e);
        }
        return this;
    }

    public UserActionsBuilder verifyField(String field, Consumer<JsonNode> verifier) {
        return verifyJson(root -> {
            JsonNode node = root.get(field);
            assertThat(node).as("Field '%s' should exist", field).isNotNull();
            verifier.accept(node);
        });
    }
}
