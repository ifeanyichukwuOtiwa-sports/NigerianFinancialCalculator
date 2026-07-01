# Implementation Plan 03 — API Documentation (springdoc-openapi / OpenAPI 3 + Swagger UI)

## Objective

Add machine- and human-readable API documentation to the `backEnd` service using
**springdoc-openapi** (OpenAPI 3.1 spec generation + an embedded Swagger UI). Every
endpoint must be:

- discoverable via an interactive Swagger UI in non-production environments,
- described with request/response schemas derived from the existing Java `record` DTOs,
- correctly marked as requiring **both** auth ingredients this API enforces: the
  `SESSION` cookie **and** the mandatory `X-App-Brand` request header,
- documented with the shared `ApiErrorResponse` error model on failure paths,
- exportable as a static `openapi.json` artifact in CI.

Swagger UI must be **off (or protected) in production** to avoid leaking the API surface
and to keep the attack surface small.

## Why this signals seniority

- **Contract-first thinking without abandoning code-first.** springdoc keeps the spec
  generated from the real controllers/DTOs, so it never drifts from the implementation —
  but we still artifact a versioned `openapi.json` in CI so consumers (frontend, QA,
  external partners) get a stable contract.
- **Security awareness.** Most teams bolt on Swagger UI and forget it is publicly
  reachable in prod. This plan explicitly gates it behind a profile/property and
  documents the *non-obvious* auth requirement (`X-App-Brand`) so integrators do not
  waste hours on 400/401s.
- **Multi-tenancy is made explicit.** The brand header enforced by `BrandContextFilter`
  is invisible in code to an outsider; surfacing it as a required OpenAPI security
  scheme is exactly the kind of tribal-knowledge-to-contract move a senior engineer makes.
- **CI as the source of truth.** Failing the build when the spec cannot be generated,
  and publishing the JSON as an artifact, turns docs into a first-class deliverable.

## Current state in this repo (cited)

- Build: `backEnd/build.gradle` — Spring Boot **4.0.5** plugin, Java toolchain **25**,
  `io.spring.dependency-management` 1.1.7, `spring-boot-starter-web` (Spring MVC /
  servlet stack). **No springdoc dependency present.**
- Controllers (all `@RestController`, all under `.../web/`):
  - `auth/web/AuthController.java` — `POST /api/auth/register`, `POST /api/auth/login`,
    `GET /api/auth/me`. (Public group; `/api/auth/**` is `permitAll`.)
  - `scenarios/web/ScenarioController.java` — `POST /api/scenarios`,
    `GET /api/scenarios`, `DELETE /api/scenarios/{id}`,
    `GET /api/scenarios/{id}/export/pdf`, `GET /api/scenarios/{id}/export/csv`.
  - `tax/web/TaxController.java` — `POST /api/tax/export/pdf`.
- DTOs are Java `record`s under `**/dto/` with Jakarta Bean Validation annotations
  already present (e.g. `auth/dto/RegisterRequest.java`, `scenarios/dto/ScenarioRequest.java`,
  `tax/dto/TaxExportRequest.java`). springdoc reads these validation constraints for
  free (min/max/required).
- Auth model:
  - Session cookie named `SESSION` (Spring Session + Redis; `deleteCookies("SESSION")`
    in `platform/security/SecurityConfig.java`).
  - Mandatory `X-App-Brand` header on **every** request, read by
    `platform/tenancy/BrandContextFilter.java` (`request.getHeader("X-App-Brand")`,
    uppercased into `BrandContext`). It is already whitelisted for CORS in
    `application.yaml` (`x-app-brand`).
- Errors: `shared/error/ApiErrorResponse.java` (record: `ErrorCode code, String uuid,
  String message, Map<String,Object> params`) with `shared/error/ErrorCode.java` enum,
  rendered centrally by `shared/error/GlobalExceptionHandler.java`.
- Security: `platform/security/SecurityConfig.java` — `permitAll` for `/api/auth/**`,
  `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/error`; **everything
  else `authenticated()`**. CSRF disabled, session `IF_REQUIRED`.
- Config: `backEnd/src/main/resources/application.yaml` (+ `application-local.yaml`).
  No Spring profiles currently activated in resources. `SERVER_PORT` defaults to 8080.
- CI: `.github/workflows/backend.yml` runs `./gradlew clean build --parallel`,
  `jacocoTestReport`, `bootJar`. No spec artifact today.
- Main class: `iwo/wintech/ngnfincalc/NGNFinancialCalcApplication.java`
  (base package `iwo.wintech.ngnfincalc`).
- Deployment: Railway (per plan brief) — public base URL differs from local `:8080`.

## Gaps

1. No OpenAPI dependency / no spec endpoint / no Swagger UI.
2. No global API metadata (title, version, servers for local + Railway).
3. The two hidden auth requirements (`SESSION` cookie, `X-App-Brand` header) are
   undocumented — an integrator cannot self-serve.
4. Swagger UI / `/v3/api-docs` are not in the security whitelist, so they would 401
   behind `anyRequest().authenticated()` if added naively — **and** if whitelisted
   naively they would be publicly reachable in production.
5. Controllers/DTOs have no `@Operation`/`@Schema` prose; error responses undocumented.
6. CI produces no `openapi.json` artifact for downstream consumers.

## Step-by-step

### Step 1 — Add the dependency (Spring Boot 4 compatible)

**File:** `backEnd/build.gradle`

Spring Boot 4 runs on **Spring Framework 7 / Jakarta Servlet 6.1** and requires
**springdoc-openapi 3.x** (the 2.x line targets Boot 3 / Spring 6 and will fail to
autoconfigure against Boot 4). Use the **webmvc** starter (this project uses Spring MVC,
not WebFlux):

```gradle
dependencies {
    // ... existing deps ...

    // OpenAPI 3 spec generation + embedded Swagger UI (Spring Boot 4 / Servlet 6.1 line).
    // 3.x is the Boot-4-compatible line; 2.x is Boot 3 only. Pin the version explicitly
    // because dependency-management does NOT manage springdoc's BOM.
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0'
}
```

> **Version note / action for the implementer:** At execution time, confirm the latest
> `3.x` release on Maven Central
> (`https://central.sonatype.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui`)
> and pin the newest `3.x`. **Do not** use any `2.x` version — those declare a
> dependency floor on Spring 6 / Boot 3 and break under Boot 4 (`NoSuchMethodError` /
> autoconfig failures). If for any reason a stable `3.x` is not yet published when you
> execute, stop and escalate rather than downgrading to 2.x.
> `springdoc-openapi-starter-webmvc-ui` transitively pulls the `webmvc-api` module
> (spec generation) **and** the Swagger UI webjar — do not add `swagger-ui` separately.

The starter bundles its own `swagger-ui` webjar, so no CDN/network access is needed at
runtime (relevant because prod may be network-restricted).

### Step 2 — Global `OpenAPI` bean (metadata + security schemes)

Create a new config class. Place it in the `platform` package alongside other
cross-cutting config.

**New file:** `backEnd/src/main/java/iwo/wintech/ngnfincalc/platform/openapi/OpenApiConfig.java`

```java
package iwo.wintech.ngnfincalc.platform.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    /** OpenAPI security scheme keys — referenced from controllers via @SecurityRequirement. */
    public static final String SESSION_COOKIE_SCHEME = "sessionCookie";
    public static final String BRAND_HEADER_SCHEME   = "brandHeader";

    @Bean
    public OpenAPI ngnFinCalcOpenAPI(
            @Value("${app.public-base-url:https://<your-app>.up.railway.app}") String publicBaseUrl,
            @Value("${server.port:8080}") String localPort) {

        return new OpenAPI()
            .info(new Info()
                .title("Nigerian Financial Calculator API")
                .description("""
                    REST API for the Nigerian Financial Calculator (compound interest,
                    scenario persistence, PDF/CSV export, and Nigerian PIT tax reporting).

                    ## Authentication
                    Two ingredients are required on **every** protected endpoint:
                    1. A valid **`SESSION`** cookie, obtained from `POST /api/auth/login`.
                    2. The **`X-App-Brand`** header identifying the tenant/brand. This
                       header is MANDATORY on every request (enforced by BrandContextFilter),
                       including the public `/api/auth/**` endpoints.
                    """)
                .version("v1")
                .contact(new Contact().name("Wintech").email("ifeanyichukwu.otiwa@pawatech.com"))
                .license(new License().name("Proprietary")))
            .servers(List.of(
                new Server().url("http://localhost:" + localPort).description("Local development"),
                new Server().url(publicBaseUrl).description("Railway (production)")))
            // Applied globally: both schemes are required on all operations.
            .addSecurityItem(new SecurityRequirement()
                .addList(SESSION_COOKIE_SCHEME)
                .addList(BRAND_HEADER_SCHEME))
            .components(new Components()
                .addSecuritySchemes(SESSION_COOKIE_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("SESSION")
                    .description("Session cookie issued by POST /api/auth/login"))
                .addSecuritySchemes(BRAND_HEADER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-App-Brand")
                    .description("MANDATORY tenant/brand header, required on every request.")));
    }
}
```

**Why a global `addSecurityItem` instead of per-controller annotations for the header:**
`X-App-Brand` is required on *every* endpoint (even `/api/auth/**`), so declaring it
once globally is DRY and cannot be forgotten on a new controller. The `SESSION` cookie
is also declared globally for simplicity; on the genuinely public auth endpoints you may
optionally clear the cookie requirement (see Step 4 note) — but keeping it global with a
clear description is acceptable and lower-maintenance.

> The two `@Value` keys (`app.public-base-url`, `server.port`) are resolved from
> `application.yaml` (Step 5). `server.port` already exists.

### Step 3 — (Optional but recommended) make the brand header show as a real parameter

Declaring it as a security scheme makes Swagger UI collect it once via the **Authorize**
dialog and send it on every "Try it out" call — this is the cleanest UX and is
sufficient. If you additionally want the header to render as an explicit, red-starred
required parameter on each operation (more discoverable in the rendered docs), add a
global `OperationCustomizer`:

**New file:** `backEnd/src/main/java/iwo/wintech/ngnfincalc/platform/openapi/BrandHeaderOperationCustomizer.java`

```java
package iwo.wintech.ngnfincalc.platform.openapi;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
public class BrandHeaderOperationCustomizer implements OperationCustomizer {

    @Override
    public io.swagger.v3.oas.models.Operation customize(
            io.swagger.v3.oas.models.Operation operation, HandlerMethod handlerMethod) {
        boolean alreadyPresent = operation.getParameters() != null
            && operation.getParameters().stream()
                .anyMatch(p -> "X-App-Brand".equalsIgnoreCase(p.getName()));
        if (!alreadyPresent) {
            operation.addParametersItem(new HeaderParameter()
                .name("X-App-Brand")
                .description("MANDATORY tenant/brand header, required on every request.")
                .required(true)
                .schema(new StringSchema().example("DEFAULT")));
        }
        return operation;
    }
}
```

> Pick **one** of: rely on the security scheme alone (simplest), or add this customizer
> too (most discoverable). Using both is fine and non-conflicting — the customizer only
> adds the visible parameter; the security scheme drives the Authorize dialog.

### Step 4 — Permit swagger/openapi paths in security WITHOUT exposing them in prod

**File:** `backEnd/src/main/java/iwo/wintech/ngnfincalc/platform/security/SecurityConfig.java`

Two changes:

1. Add the springdoc paths to the `permitAll` list so Swagger UI works in
   non-prod (otherwise `anyRequest().authenticated()` blocks them). The default
   springdoc paths are `/v3/api-docs/**` and `/swagger-ui/**` (the latter includes the
   `/swagger-ui.html` redirect and the webjar assets).
2. Gate that permit block behind whether Swagger is enabled, so in production the paths
   are simply **not served** (Step 5 disables the endpoints entirely) AND are not
   whitelisted. Belt-and-suspenders: even if a path leaked, it would 401.

**Diff (conceptual):**

```diff
 @ConfigurationPropertiesScan
 @EnableWebSecurity
 @Configuration
 public class SecurityConfig {

     @Bean
     public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                    final BrandContextFilter brandContextFilter,
                                                    final RateLimitingFilter rateLimitingFilter,
-                                                   final CorsConfig config) {
+                                                   final CorsConfig config,
+                                                   @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerEnabled) {
         http
             .cors(cors -> cors.configurationSource(corsConfigurationSource(config)))
             .csrf(AbstractHttpConfigurer::disable)
             .addFilterBefore(brandContextFilter, UsernamePasswordAuthenticationFilter.class)
             .addFilterBefore(rateLimitingFilter, BrandContextFilter.class)
             .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
             .securityContext(sc -> sc
                 .securityContextRepository(new HttpSessionSecurityContextRepository())
             )
             .authorizeHttpRequests(auth -> auth
                 .requestMatchers("/api/auth/**").permitAll()
                 .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                 .requestMatchers("/error").permitAll()
+                // Only whitelist the docs when Swagger is explicitly enabled (non-prod).
+                // In prod swaggerEnabled=false => these paths are neither served nor permitted.
+                .requestMatchers(swaggerEnabled
+                        ? new String[]{"/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"}
+                        : new String[]{})
+                    .permitAll()
                 .anyRequest().authenticated()
             )
```

Add the import:

```java
import org.springframework.beans.factory.annotation.Value;
```

> **Note on the empty-array matcher:** `requestMatchers(new String[]{})` with no paths is
> a harmless no-op registration. If your Spring Security version rejects an empty
> varargs, guard it with an `if (swaggerEnabled) { ... }` inside the lambda instead —
> functionally identical.

> **Note on the `X-App-Brand` filter and docs paths:** `BrandContextFilter` does not
> reject requests lacking the header (it only sets context when present), so Swagger UI
> static assets load fine without the brand header. No change needed there. Downstream,
> the *actual* API calls made via "Try it out" will need the header — which is exactly
> what the Authorize dialog / customizer supplies.

### Step 5 — Config toggles (default OFF, on per-profile)

springdoc exposes `springdoc.api-docs.enabled` (the `/v3/api-docs` JSON) and
`springdoc.swagger-ui.enabled` (the UI). Default both to **disabled** globally, then
enable them only in the `local` profile (and any non-prod profile you use).

**File:** `backEnd/src/main/resources/application.yaml` — add:

```yaml
app:
  public-base-url: ${APP_PUBLIC_BASE_URL:https://<your-app>.up.railway.app}

springdoc:
  api-docs:
    enabled: ${SPRINGDOC_ENABLED:false}   # spec JSON off by default (prod-safe)
    path: /v3/api-docs
  swagger-ui:
    enabled: ${SPRINGDOC_ENABLED:false}   # UI off by default (prod-safe)
    path: /swagger-ui.html
    # Keep operations sorted for stable, reviewable docs.
    operations-sorter: alpha
    tags-sorter: alpha
  # Group only our API surface; hide framework/actuator noise.
  paths-to-match: /api/**
```

**File:** `backEnd/src/main/resources/application-local.yaml` — add (turn it ON locally):

```yaml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true

app:
  public-base-url: http://localhost:8080
```

> **Production stance:** Because the default is `false`, Railway (which does not set
> `SPRINGDOC_ENABLED`/`SPRING_PROFILES_ACTIVE=local`) serves **no** docs endpoints. To
> temporarily enable docs in a protected staging env, set `SPRINGDOC_ENABLED=true`.
> Combined with Step 4's conditional whitelist, when disabled the paths are both unserved
> and unpermitted. If you ever must expose Swagger in a prod-like env, put it behind
> `authenticated()` instead of `permitAll` (drop the path from the whitelist so it falls
> through to `anyRequest().authenticated()`), and reach it with a valid session + brand.

### Step 6 — Annotate controllers

Add class-level `@Tag` and method-level `@Operation` + `@ApiResponse`s. Reference the
security schemes where you want them explicit (the global item already applies them, so
these are mostly for prose/overrides). Document `ApiErrorResponse` as the error schema.

**Example — `auth/web/AuthController.java`:**

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import iwo.wintech.ngnfincalc.shared.error.ApiErrorResponse;

@Tag(name = "Authentication", description = "Registration, login, and current-user lookup")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Operation(
        summary = "Register a new user",
        description = "Creates a user for the brand supplied in X-App-Brand and starts a session.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Registered and authenticated"),
        @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Email already exists",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) { ... }

    @Operation(summary = "Log in", description = "Authenticates and issues the SESSION cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated"),
        @ApiResponse(responseCode = "401", description = "Bad credentials",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(...) { ... }

    @Operation(summary = "Get current user",
        description = "Returns the authenticated user for the current session + brand.")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me() { ... }
}
```

**Example — `scenarios/web/ScenarioController.java`** (note the binary export responses):

```java
@Tag(name = "Scenarios", description = "Persist and export compound-interest scenarios")
// ...
@Operation(summary = "Export a scenario as PDF")
@ApiResponse(responseCode = "200", description = "PDF bytes",
    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
        schema = @Schema(type = "string", format = "binary")))
@ApiResponse(responseCode = "404", description = "Scenario not found",
    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@GetMapping("/{id}/export/pdf")
public ResponseEntity<byte[]> exportToPdf(
    @Parameter(description = "Scenario id") @PathVariable Long id) { ... }
```

**Example — `tax/web/TaxController.java`:**

```java
@Tag(name = "Tax", description = "Nigerian PIT calculation and export")
// ...
@Operation(summary = "Export a Nigerian PIT tax report as PDF",
    description = "Uses the brand's tax bands (resolved from X-App-Brand) for the given annual income.")
@ApiResponse(responseCode = "200", description = "PDF bytes",
    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
        schema = @Schema(type = "string", format = "binary")))
@PostMapping("/export/pdf")
public ResponseEntity<byte[]> exportTaxPdf(@RequestBody TaxExportRequest request) { ... }
```

### Step 7 — Annotate record DTOs

`record` components map cleanly to `@Schema` on the component. springdoc already reads
the Jakarta validation annotations (`@NotBlank`, `@Size`, `@DecimalMin`, `@Min`,
`@Email`) to mark fields required and set min/max — so annotations here are for
descriptions/examples, not to restate constraints.

**Example — `auth/dto/RegisterRequest.java`:**

```java
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "New-user registration payload")
public record RegisterRequest(
    @Schema(description = "User email", example = "ada@example.com")
    @NotBlank @Email String email,

    @Schema(description = "Password (8-64 chars)", example = "S3curePass!")
    @NotBlank @Size(min = 8, max = 64) String password,

    @Schema(description = "Full display name", example = "Ada Lovelace")
    @NotBlank String fullName
) {}
```

**Example — `scenarios/dto/ScenarioRequest.java`** (money as `BigDecimal` renders as
`number`; add examples for the discriminating string fields):

```java
@Schema(description = "Scenario inputs to persist and compute")
public record ScenarioRequest(
    @Schema(description = "Brand override; usually derived from X-App-Brand", nullable = true) String brand,
    @Schema(example = "Retirement plan") @NotBlank String name,
    @Schema(example = "1000000.00") @NotNull @DecimalMin("0.0") BigDecimal principal,
    @Schema(example = "0.15", description = "Annual rate as a decimal fraction") @NotNull @DecimalMin("0.0") BigDecimal annualRate,
    @Schema(example = "10") @NotNull @Min(1) Integer years,
    @Schema(example = "50000.00") @NotNull @DecimalMin("0.0") BigDecimal monthlyContribution,
    @Schema(example = "MONTHLY", description = "Compounding frequency") @NotBlank String compoundingFrequency,
    @Schema(example = "TAX_DEFERRED", description = "Tax strategy") @NotBlank String taxStrategy,
    @Schema(example = "5000000.00") @DecimalMin("0.0") BigDecimal annualIncome
) {}
```

**Error model — `shared/error/ApiErrorResponse.java`:** annotate so it renders as the
canonical error body wherever referenced via `@Schema(implementation = ApiErrorResponse.class)`:

```java
@Schema(description = "Standard error envelope returned by GlobalExceptionHandler")
public record ApiErrorResponse(
    @Schema(description = "Machine-readable error code") ErrorCode code,
    @Schema(description = "Correlation id for this error", example = "3f1c...") String uuid,
    @Schema(description = "Human-readable message", example = "Invalid email format") String message,
    @Schema(description = "Optional structured details", nullable = true) Map<String, Object> params
) { ... }
```

`ErrorCode` enum values render automatically as the schema's `enum` list — no annotation
needed, but you may add `@Schema(description = "...")` on the enum type.

### Step 8 — Generate a static `openapi.json` in CI

Two viable approaches; **prefer A** (no runtime boot, deterministic).

**Approach A — `springdoc-openapi-gradle-plugin`.** Add to `backEnd/build.gradle`:

```gradle
plugins {
    // ... existing ...
    id 'org.springdoc.openapi-gradle-plugin' version '2.0.0'  // confirm latest at execution time
}

openApi {
    apiDocsUrl.set("http://localhost:8080/v3/api-docs")
    outputDir.set(layout.buildDirectory.dir("openapi"))
    outputFileName.set("openapi.json")
    // The plugin boots the app to scrape the spec; docs must be enabled for that run.
    customBootRun {
        args.set(["--springdoc.api-docs.enabled=true",
                  "--spring.profiles.active=local"])
    }
}
```

This adds a `generateOpenApiDocs` task that starts the app, hits `/v3/api-docs`, and
writes `build/openapi/openapi.json`.

> **Caveat:** the plugin boots the full context, so CI needs the app's runtime deps
> (MySQL, Redis) reachable or those beans made lazy/mocked. This repo's context needs a
> datasource + Redis; if standing those up in CI is heavy, use **Approach B**.

**Approach B — MockMvc test that writes the spec (no external infra).** Add a
`@WebMvcTest`/`@SpringBootTest`-lite slice that pulls `/v3/api-docs` and writes it to
`build/openapi/openapi.json`. This keeps generation inside the existing test phase and
avoids booting Redis/MySQL if you slice the context. Given this repo already uses
Testcontainers, a small integration test hitting `/v3/api-docs` (with `SPRINGDOC_ENABLED=true`)
and dumping the body to a file is straightforward and reuses existing infra.

**CI wiring — `.github/workflows/backend.yml`,** append after the JAR step:

```yaml
      - name: Generate OpenAPI spec
        working-directory: backEnd
        run: ./gradlew generateOpenApiDocs   # (Approach A); or the test task for Approach B

      - name: Upload OpenAPI spec
        uses: actions/upload-artifact@v4
        with:
          name: openapi-spec
          path: backEnd/build/openapi/openapi.json
          if-no-files-found: error
```

## Wiring

- `OpenApiConfig` is a `@Configuration` in `iwo.wintech.ngnfincalc.platform.openapi`,
  inside the base package `iwo.wintech.ngnfincalc`, so it is component-scanned by
  `NGNFinancialCalcApplication` — no manual registration.
- `BrandHeaderOperationCustomizer` (if used) is a `@Component`, auto-detected by
  springdoc's `OperationCustomizer` mechanism.
- `SecurityConfig` gains a `@Value("${springdoc.swagger-ui.enabled:false}")` param; it
  is a `@Bean` method so injection works without extra wiring.
- Property resolution: `springdoc.*` and `app.public-base-url` live in
  `application.yaml` (defaults, prod-safe OFF) and are overridden ON in
  `application-local.yaml`. Runtime override via `SPRINGDOC_ENABLED` /
  `APP_PUBLIC_BASE_URL` / `SPRING_PROFILES_ACTIVE`.
- No new beans conflict with existing security filters; docs paths bypass
  `BrandContextFilter`'s (non-blocking) logic naturally.

## Verification

1. **Build compiles:** `cd backEnd && ./gradlew compileJava` — confirms the springdoc
   3.x dependency resolves against Boot 4 with no `NoSuchMethodError`.
2. **Local run with docs ON:**
   `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` then:
   - `curl -s http://localhost:8080/v3/api-docs | jq '.openapi'` → prints `3.x.x`.
   - `curl -s http://localhost:8080/v3/api-docs | jq '.components.securitySchemes'`
     → shows both `sessionCookie` (cookie/`SESSION`) and `brandHeader`
     (header/`X-App-Brand`).
   - Open `http://localhost:8080/swagger-ui.html` → UI renders; **Authorize** dialog
     shows both scheme inputs; every operation lists `X-App-Brand` as required (if the
     customizer is used).
   - "Try it out" on `POST /api/auth/login` succeeds when brand + body supplied.
3. **Prod-safe by default (docs OFF):** run with **no** `local` profile and no
   `SPRINGDOC_ENABLED`:
   - `curl -i http://localhost:8080/v3/api-docs` → **404** (endpoint disabled).
   - `curl -i http://localhost:8080/swagger-ui.html` → **404**.
   - Confirm existing endpoints still behave: `curl -i http://localhost:8080/api/scenarios`
     without a session/brand → **401** (unchanged security posture).
4. **Error model present:** in the spec JSON,
   `jq '.components.schemas.ApiErrorResponse'` shows `code/uuid/message/params` and
   `.components.schemas.ErrorCode.enum` lists all `ErrorCode` values.
5. **Existing tests green:** `./gradlew test` — no regressions from SecurityConfig change.
6. **CI artifact:** push a branch; confirm the `openapi-spec` artifact is produced and
   downloadable, and `jq '.info.version'` == `v1`.

## Best-practice notes & pitfalls

- **Prod exposure (the big one).** Default `springdoc.*.enabled=false` **and**
  conditionally whitelist the paths only when enabled. Never leave `/swagger-ui/**`
  `permitAll` in an environment where the API is internet-facing unless you intend for
  the schema to be public. If docs must exist in prod, protect them with
  `authenticated()` rather than `permitAll`.
- **Keep the required header discoverable.** The `X-App-Brand` requirement is invisible
  in code to an integrator. Declaring it as a global security scheme (and optionally a
  visible required parameter) is the single most valuable part of this doc effort — do
  not drop it to save a few lines.
- **Version the spec deliberately.** `info.version = v1` is the *API* version, distinct
  from the Gradle `project.version` (`0.0.1-SNAPSHOT`). When you introduce breaking
  changes, bump `info.version` and consider path/prefix versioning; keep old
  `openapi.json` artifacts for consumers.
- **springdoc line must match Boot line.** 3.x ↔ Boot 4 / Spring 7; 2.x ↔ Boot 3.
  A mismatch is the classic failure mode (autoconfig/`NoSuchMethodError`). Pin explicitly
  since dependency-management does not manage the springdoc BOM.
- **Use webmvc, not webflux.** This project is servlet MVC (`spring-boot-starter-web`).
  The `-webflux-ui` starter would silently mis-wire.
- **Examples earn their keep.** Money fields are `BigDecimal` → rendered as JSON
  `number`; add `example` values so integrators know scale/format. For binary export
  endpoints, declare `schema(type="string", format="binary")` with the correct media
  type so the UI does not try to render PDF/CSV bytes as JSON.
- **Rate limiting.** `RateLimitingFilter` runs before the docs; heavy "Try it out"
  clicking can trip the 20-req/min bucket. Fine for docs, but note it if QA reports 429s.
- **CSRF is disabled** already, so Swagger UI "Try it out" POSTs work without a token.
  If CSRF is ever enabled, expose the token to Swagger UI or exempt it.
- **CI generation cost.** Approach A boots the app (needs DB/Redis in CI). If that is
  too heavy, use Approach B (MockMvc/Testcontainers dump) to keep generation cheap and
  hermetic.
- **Do not annotate to duplicate validation.** springdoc already derives
  required/min/max from the Jakarta constraints on the records; `@Schema` should add
  prose/examples, not restate constraints (avoids drift).

## Effort estimate

| Task | Est. |
|------|------|
| Dependency + global `OpenApiConfig` bean (schemes, servers, info) | 1.0–1.5 h |
| SecurityConfig conditional whitelist + property toggles | 0.5–1.0 h |
| Optional `BrandHeaderOperationCustomizer` | 0.5 h |
| Annotate 3 controllers (`@Tag`/`@Operation`/`@ApiResponse`) | 1.0–1.5 h |
| Annotate DTO records + `ApiErrorResponse` | 1.0 h |
| CI `openapi.json` generation + artifact | 1.0–2.0 h (A vs B) |
| Verification (local ON, prod OFF, tests, CI) | 1.0 h |
| **Total** | **~6–8.5 h** |

## References

- springdoc-openapi docs: https://springdoc.org/
- Maven Central (pin latest 3.x):
  https://central.sonatype.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui
- springdoc Gradle plugin: https://github.com/springdoc/springdoc-openapi-gradle-plugin
- OpenAPI 3.1 Specification: https://spec.openapis.org/oas/latest.html
- Spring Boot 4 (Spring Framework 7 / Servlet 6.1) — dependency compatibility rationale.
- Repo anchors: `backEnd/build.gradle`, `platform/security/SecurityConfig.java`,
  `platform/tenancy/BrandContextFilter.java`, `shared/error/ApiErrorResponse.java`,
  `auth/web/AuthController.java`, `scenarios/web/ScenarioController.java`,
  `tax/web/TaxController.java`, `backEnd/src/main/resources/application.yaml`,
  `.github/workflows/backend.yml`.
