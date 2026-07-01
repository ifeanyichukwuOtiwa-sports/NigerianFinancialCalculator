# 06 — Test Strategy Made Rigorous and Visible

> Coverage gating + reporting, mutation testing, frontend unit + e2e in CI, load testing.
>
> Repo: `nigerian-financial-calculator` (Spring Boot 4 / Java 25 backend, Angular 21 frontend).
> This plan is concrete and repo-grounded. A developer should be able to execute it top-to-bottom.

---

## 1. Objective

Turn the project's testing from "tests exist but nobody can see the signal" into a **rigorous, gated, and visible** quality system:

- Coverage is **measured, gated, uploaded, and badged** — a PR that drops coverage on financial-math packages fails.
- The financial math (`CalculationService`, `TaxService`) is **mutation-tested**, proving the tests actually assert on the numbers rather than just executing the lines.
- The frontend runs **unit tests with coverage** and **Playwright e2e** in CI (today it runs neither).
- The calculation endpoint has a **load test with SLO thresholds** so performance regressions are caught.
- Everything is documented via a **test pyramid + naming convention** and surfaced through **README badges**.

## 2. Why this signals seniority

- Junior/mid engineers write tests. Senior engineers make quality **visible and enforced** — gates in CI, coverage trend, mutation score. The difference between "we have tests" and "our tests are proven to catch defects" is exactly mutation testing.
- For **financial software**, line coverage is a vanity metric. A test can execute `totalInterest.subtract(estimatedTax)` and assert nothing meaningful. Mutation testing (flip `-` to `+`, `HALF_UP` to `HALF_DOWN`, `<=` to `<`) is the only cheap way to prove the assertions actually constrain the money math.
- Wiring the **already-configured-but-never-run** SonarCloud step demonstrates you read the build, not just added new tooling. The repo already declares `sonarqube` + `jacoco` (`backEnd/build.gradle`) but CI never invokes `sonar` — closing that loop is a high-signal, low-cost win.

---

## 3. Current state in this repo

### Backend (`backEnd/build.gradle`)
- **JaCoCo is present** — plugin `id 'jacoco'`, `toolVersion = "0.8.14"`, and `jacocoTestReport` with `xml.required = true` (lines 5, 81–90).
- **SonarCloud is configured** — plugin `org.sonarqube` 6.0.1.5171 (line 6); `sonar { properties { ... } }` block (lines 92–100) sets:
  - `sonar.projectKey` = `ifeanyichukwuOtiwa-sports_NigerianFinancialCalculator_backend`
  - `sonar.organization` = `ifeanyichukwuotiwa-sports`
  - `sonar.host.url` = `https://sonarcloud.io`
  - `sonar.java.coveragePlugin` = `jacoco`
  - `sonar.coverage.jacoco.xmlReportPaths` → the `jacocoTestReport.xml` path.
- **Tests use Testcontainers** — `backEnd/src/test/java/iwo/wintech/ngnfincalc/TestcontainersConfiguration.java` spins a `MySQLContainer(mysql:latest)` and a `GenericContainer(redis:latest)` wired via `@ServiceConnection`. **No `.withReuse(true)`** today, so every `@SpringBootTest` pays full container startup.
- **Existing tests** (`find src/test`):
  - `flow/UserFlowIntegrationTest.java` — full `@SpringBootTest(RANDOM_PORT)` lifecycle test (register → login → create None/WHT/Progressive scenarios → list → delete → logout) via `TestRestTemplate`; imports `TestcontainersConfiguration`.
  - `service/TaxServiceTest.java` — pure unit test of `TaxService.calculateDetailedPIT` (no Spring context; `new TaxService()`), with the 2026 Nigerian PIT bands. Good model to copy.
  - `auth/repository/UserRepositoryIntegrationTest.java`, `scenarios/repository/InvestmentScenarioRepositoryIntegrationTest.java` — repository integration tests (Testcontainers-backed).
  - `NGNFinancialCalcApplicationTests.java` (context load), `TestBackEndApplication.java`, `flow/UserActionsBuilder.java` (test helper).
- **Pure business logic** worth mutation testing:
  - `scenarios/service/CalculationService.java` — compound interest: `(1 + r/n)^(nt)`, annuity future value, then `WHT`/`progressive` tax subtraction. Heavy `BigDecimal` + `RoundingMode.HALF_UP` + branch on `taxStrategy`.
  - `tax/service/TaxService.java` — progressive PIT across bands, effective-rate division, band-fill edge case. Pure, no Spring deps.
- **The calculation "endpoint"** is `POST /api/scenarios` (`scenarios/web/ScenarioController.java`) → `ScenarioService.saveScenario` → `CalculationService.calculateFutureBalance`. There is no standalone `/calculate` endpoint; the save path IS the calc path. Request DTO is `scenarios/dto/ScenarioRequest` (fields: `brand`, `name`, `principal`, `annualRate`, `years`, `monthlyContribution`, `compoundingFrequency`, `taxStrategy`, `annualIncome`).

### Frontend
- `frontEnd/package.json`: `test` = `ng test`, `test:e2e` = `playwright test`, `test:e2e:headed`.
- `frontEnd/angular.json`: `test` builder = **`@angular/build:unit-test`** (Angular 21's new unit-test runner) — dev deps include **`vitest` ^4** and `jsdom`, so this is **Vitest under the hood, NOT Karma**. Plan snippets below target Vitest.
- `frontEnd/playwright.config.ts`: `testDir: ./e2e`, chromium headless, `workers: 1` in CI, `webServer` auto-runs `npm start` on `127.0.0.1:4200` (`reuseExistingServer: !CI`). Note: it starts **only the frontend dev server**; the backend is proxied via `proxy.conf.json`.

### CI
- `.github/workflows/backend.yml` — checkout → setup Java 25 → `gradle/actions/setup-gradle@v4` → `./gradlew clean build --parallel` → `./gradlew jacocoTestReport` → `./gradlew bootJar`. **No Sonar step, no coverage upload, no gate, no mutation testing.**
- `.github/workflows/frontend.yml` — checkout → setup Node 22 → `npm ci` → `npx ng lint` → `npx ng build`. **Runs NO tests at all** (neither unit nor e2e).

## 4. Gaps

| # | Gap | Impact |
|---|-----|--------|
| G1 | JaCoCo report is generated but **never gated** — no `jacocoTestCoverageVerification`. | Coverage can silently rot; no PR fails on regression. |
| G2 | SonarCloud fully configured but **`sonar` task is never run in CI**; JaCoCo XML never uploaded anywhere. | Zero visibility of coverage/quality; the config is dead code. |
| G3 | No **mutation testing** on the money math. Line coverage on `CalculationService`/`TaxService` does not prove the assertions constrain the numbers. | Silent financial defects (rounding, sign, band boundaries) can pass a "green" build. |
| G4 | Testcontainers has **no reuse**; every integration test cold-starts MySQL+Redis. Full `@SpringBootTest` is used where slices would do. | Slow CI, slow local loop. |
| G5 | **Frontend unit tests do not run in CI**; no coverage produced. | Angular regressions ship undetected. |
| G6 | **Playwright e2e does not run in CI**; also can't currently exercise the full flow because CI has no backend/DB. | The one real end-to-end signal is unused. |
| G7 | No **load test** for the calculation path; no performance SLO. | Perf regressions in `BigDecimal.pow` / DB fan-out invisible until prod. |
| G8 | No **badges**; no test pyramid / naming convention doc. | Quality is invisible to reviewers and newcomers. |

---

## 5. Step-by-step implementation

Ordering rationale: land the **gate + upload** first (immediate visible signal), then **mutation testing** (proves the gate is meaningful), then **speed**, then **frontend**, then **load** + docs.

### Step 1 — Coverage GATE via `jacocoTestCoverageVerification`

**File:** `backEnd/build.gradle` — append after the existing `sonar { ... }` block (after line 100).

Rationale for thresholds: gate **hard** on the pure-logic packages (`scenarios.service`, `tax.service`) where a missed branch = a money bug; gate **moderately** on web/service overall; **exclude** DTOs, config, mappers, the Spring boot main class, and generated Lombok surface where line coverage is noise.

```gradle
// ── Coverage gate ─────────────────────────────────────────────────────────────
// Fails the build when critical financial-math packages fall below threshold.
// Run explicitly or as part of `check`.
def jacocoExcludes = [
    'iwo/wintech/ngnfincalc/NGNFinancialCalcApplication.class',
    '**/dto/**',
    '**/config/**',        // BrandTaxConfig, mappers, Spring @Configuration
    '**/*Request.class',
    '**/*Response.class',
    '**/*Mapper*',
]

tasks.named('jacocoTestReport') {
    // keep xml.required (already set) + add HTML for humans
    reports {
        xml.required = true
        html.required = true
    }
    afterEvaluate {
        classDirectories.setFrom(
            files(classDirectories.files.collect {
                fileTree(dir: it, exclude: jacocoExcludes)
            })
        )
    }
}

tasks.register('jacocoCoverageVerification', JacocoCoverageVerification) {
    dependsOn tasks.named('test')
    executionData tasks.named('test')
    sourceDirectories.setFrom(files(sourceSets.main.allSource.srcDirs))
    classDirectories.setFrom(
        files(sourceSets.main.output.collect {
            fileTree(dir: it, exclude: jacocoExcludes)
        })
    )

    violationRules {
        // Global floor for the whole module.
        rule {
            limit {
                counter = 'LINE'
                minimum = 0.60
            }
        }
        // Hard gate: compound-interest math.
        rule {
            element = 'PACKAGE'
            includes = ['iwo.wintech.ngnfincalc.scenarios.service']
            limit { counter = 'LINE';   minimum = 0.90 }
            limit { counter = 'BRANCH'; minimum = 0.85 }
        }
        // Hard gate: progressive tax math.
        rule {
            element = 'PACKAGE'
            includes = ['iwo.wintech.ngnfincalc.tax.service']
            limit { counter = 'LINE';   minimum = 0.90 }
            limit { counter = 'BRANCH'; minimum = 0.85 }
        }
    }
}

// Make the gate part of the standard verification lifecycle.
tasks.named('check') {
    dependsOn tasks.named('jacocoCoverageVerification')
}
```

> Start the global floor at `0.60` (roughly today's reality — measure first with `./gradlew jacocoTestReport` and read `build/reports/jacoco/test/html/index.html`) and **ratchet it up** over time. Never set a floor above current coverage on day one or you block every PR immediately.

**Prerequisite tests to reach the 90% gate on the math packages** — add before enabling the gate, otherwise the build breaks:
- New file `backEnd/src/test/java/iwo/wintech/ngnfincalc/scenarios/service/CalculationServiceTest.java` — unit test `calculateFutureBalance` with mocked `TaxService`/`TaxBandRepository`/`BrandTaxConfigMapper` (Mockito is on the classpath via `spring-boot-starter-test`). Cover: `taxStrategy` = `none` / `wht` / `progressive` / unknown-default; `periodsPerYear` null → 12; zero-rate annuity branch (`calculateAnnuity` when `periodicRate == 0`); a known compound-interest golden value; scale/rounding on all six `CalculationResult` fields.
- Extend `service/TaxServiceTest.java` — add band-boundary cases (income exactly at a limit), top open-ended band (`limit == null`), negative income, effective-rate rounding.

### Step 2 — Upload coverage to SonarCloud (+ optional Codecov + PR comment)

**2a. Wire the missing Sonar step into `.github/workflows/backend.yml`.**

Replace the "Generate JaCoCo report" / "Build JAR" tail with a single ordered run (Sonar needs the JaCoCo XML to already exist) and add a gate step:

```yaml
      - name: Test + coverage report + gate
        working-directory: backEnd
        run: ./gradlew test jacocoTestReport jacocoCoverageVerification --parallel

      - name: SonarCloud analysis
        working-directory: backEnd
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: ./gradlew sonar --info

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v5
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: backEnd/build/reports/jacoco/test/jacocoTestReport.xml
          flags: backend

      - name: Build JAR
        working-directory: backEnd
        run: ./gradlew bootJar
```

Also add, at job level (needed for Sonar PR decoration and accurate new-code coverage):

```yaml
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0   # Sonar needs full history for new-code analysis
```

**Secrets to add** (repo Settings → Secrets → Actions): `SONAR_TOKEN` (from SonarCloud), and `CODECOV_TOKEN` if using Codecov. Sonar PR comments come for free once the SonarCloud GitHub App is installed on the repo and `SONAR_TOKEN` is set — no extra CI step. For a Codecov PR coverage comment, add `frontEnd`/`backEnd` flags and a `codecov.yml` at repo root:

```yaml
# codecov.yml (repo root)
coverage:
  status:
    project:
      default:
        target: auto
        threshold: 1%     # tolerate 1% drop before failing the check
    patch:
      default:
        target: 80%       # new/changed lines must be 80% covered
comment:
  layout: "reach, diff, flags, files"
flags:
  backend:
    paths: [backEnd/]
  frontend:
    paths: [frontEnd/]
```

> Pick **one** primary source of truth for the PR comment (SonarCloud OR Codecov) to avoid two bots arguing on every PR. SonarCloud is already configured here, so it's the natural primary; treat Codecov as optional/secondary.

### Step 3 — PIT mutation testing (`info.solidsoft.pitest`)

**Why mutation testing for financial math:** JaCoCo tells you a line ran; PIT tells you whether a test would **fail if that line were wrong**. PIT rewrites the bytecode — flipping `subtract`→`add`, `HALF_UP`→`HALF_DOWN`, `<= 0`→`< 0`, removing a `.setScale(...)` call, negating a conditional — and reruns your tests. A "surviving mutant" is a change to the money math that **no test noticed**. For `CalculationService` (sign of interest, rounding, tax-strategy branch) and `TaxService` (band boundaries, effective-rate division) this is exactly where silent, expensive defects live. Scope PIT tightly to those two packages so runtime stays reasonable.

**File:** `backEnd/build.gradle`.

Add the plugin (top `plugins { }` block):
```gradle
    id 'info.solidsoft.pitest' version '1.15.0'
```

Add configuration block (after the coverage block from Step 1):
```gradle
// ── Mutation testing (PIT) — scoped to the pure financial math only ───────────
pitest {
    pitestVersion = '1.19.1'
    junit5PluginVersion = '1.2.1'          // required for JUnit 5 test discovery

    targetClasses = [
        'iwo.wintech.ngnfincalc.scenarios.service.CalculationService',
        'iwo.wintech.ngnfincalc.tax.service.TaxService'
    ]
    targetTests = [
        'iwo.wintech.ngnfincalc.scenarios.service.*',
        'iwo.wintech.ngnfincalc.service.*'   // TaxServiceTest currently lives here
    ]

    // STRONGER default set catches arithmetic/rounding/boundary mutants that
    // matter for money. Avoid the full experimental set (huge runtime, noisy).
    mutators = ['STRONGER']

    threads = 4
    timestampedReports = false
    outputFormats = ['HTML', 'XML']

    // Fail the build if the tests can't kill enough mutants.
    mutationThreshold = 80      // % mutants killed — realistic, ratchet toward 90
    coverageThreshold = 90      // line coverage PIT itself sees on target classes
}
```

> Notes:
> - PIT runs **only** the tests matching `targetTests` against **only** the `targetClasses` — it will NOT boot the Spring context or Testcontainers, so it stays fast (seconds, not minutes). This is deliberate; keep it that way.
> - `CalculationService` depends on collaborators (`TaxService`, repo, mapper). Its unit test (Step 1) must supply mocks so PIT can mutate `CalculationService` in isolation without needing a container.
> - Start `mutationThreshold` at `80`. Run once, read `build/reports/pitest/index.html`, look at **surviving mutants**, add assertions to kill them, then ratchet to `90`.

**CI:** add to `.github/workflows/backend.yml` (after the Sonar step). Keep it non-blocking-to-optional at first via a dedicated invocation:
```yaml
      - name: Mutation testing (financial math)
        working-directory: backEnd
        run: ./gradlew pitest

      - name: Upload PIT report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: pitest-report
          path: backEnd/build/reports/pitest/
```
The `pitest` task fails the build if `mutationThreshold` isn't met — that IS the gate. Because it's scoped to two classes, the runtime cost is small.

### Step 4 — Testcontainers speed

**4a. Enable container reuse locally.** Add to each developer's (and the CI runner's) `~/.testcontainers.properties`:
```properties
testcontainers.reuse.enable=true
```

**4b. Opt the containers in** — `TestcontainersConfiguration.java`:
```java
@Bean
@ServiceConnection
public MySQLContainer<?> mysqlContainer() {
    return new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))   // pin, not :latest
            .withReuse(true);
}

@Bean
@ServiceConnection(name = "redis")
public GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379)
            .withReuse(true);
}
```
Two correctness improvements bundled in: **pin image tags** (`mysql:latest`/`redis:latest` make builds non-reproducible and can silently break) and `.withReuse(true)`.

> Reuse caveat: reused containers keep their state between runs, so tests must not assume a pristine DB. The repository tests must clean up after themselves (or use transactional rollback / truncate-in-`@BeforeEach`). Note also that on ephemeral CI runners reuse gives little benefit (fresh VM per job); reuse mainly speeds the **local** loop. On CI, prefer running the whole suite in one Gradle invocation so the container starts once per JVM.

**4c. Cost of full-context `@SpringBootTest`.** `UserFlowIntegrationTest` uses `@SpringBootTest(RANDOM_PORT)` — appropriate for one end-to-end journey. But do **not** reach for full context for everything:
- Pure logic (`TaxService`, `CalculationService`) → **plain JUnit + Mockito**, no Spring, no container (as `TaxServiceTest` already does — this is the model).
- Web layer in isolation → `@WebMvcTest`.
- Persistence in isolation → `@JdbcTest` / `@DataJdbcTest` + one shared Testcontainer.
- Full `@SpringBootTest` → reserve for the 1–2 real journey tests.

Every full-context test forces a fresh application context (unless cached by identical config) and container startup. Keep them few; let the pyramid do the work.

### Step 5 — Frontend: unit tests with coverage + Playwright e2e in CI

**5a. Add a coverage-capable test script** — `frontEnd/package.json` scripts:
```json
"test": "ng test",
"test:ci": "ng test --no-watch --code-coverage --browsers=ChromeHeadless",
```
(Angular 21's `@angular/build:unit-test` builder runs Vitest; `--code-coverage` emits an lcov report under `coverage/`. `--browsers=ChromeHeadless` applies to the browser-based runner; if the project runs Vitest in jsdom the flag is a harmless no-op and `--no-watch --code-coverage` are the load-bearing flags. Verify locally with `npm run test:ci` and confirm `coverage/lcov.info` appears.)

**5b. Rewrite `.github/workflows/frontend.yml`** to run lint → unit (with coverage) → build → e2e:
```yaml
name: Frontend CI

on:
  push:
    branches: [develop, main]
    paths: ['frontEnd/**']
  pull_request:
    branches: [develop, main]
    paths: ['frontEnd/**']

jobs:
  unit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: frontEnd/package-lock.json
      - name: Install dependencies
        working-directory: frontEnd
        run: npm ci
      - name: Lint
        working-directory: frontEnd
        run: npx ng lint
      - name: Unit tests + coverage
        working-directory: frontEnd
        run: npm run test:ci
      - name: Upload frontend coverage to Codecov
        uses: codecov/codecov-action@v5
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: frontEnd/coverage/lcov.info
          flags: frontend
      - name: Build
        working-directory: frontEnd
        run: npx ng build

  e2e:
    runs-on: ubuntu-latest
    needs: unit
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: frontEnd/package-lock.json
      - name: Install dependencies
        working-directory: frontEnd
        run: npm ci
      - name: Install Playwright browsers
        working-directory: frontEnd
        run: npx playwright install --with-deps chromium
      - name: Run Playwright e2e
        working-directory: frontEnd
        run: npm run test:e2e
      - name: Upload Playwright report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-report
          path: frontEnd/playwright-report/
          retention-days: 7
```

**5c. Backend for e2e.** `playwright.config.ts` only starts the Angular dev server (backend reached via `proxy.conf.json`). Two viable strategies — pick per test:
- **Mocked backend (recommended for CI default):** use Playwright's `page.route('**/api/**', ...)` to stub `/api/scenarios`, `/api/auth/*`, etc. Fast, deterministic, no DB. Best for UI-behavior e2e.
- **Real backend (for a smoke journey):** add a `services:` block to the `e2e` job to run MySQL + Redis, then start the backend jar before Playwright:
  ```yaml
      services:
        mysql:
          image: mysql:8.4
          env: { MYSQL_ROOT_PASSWORD: root, MYSQL_DATABASE: ngncalc }
          ports: ['3306:3306']
          options: >-
            --health-cmd="mysqladmin ping" --health-interval=10s
            --health-timeout=5s --health-retries=5
        redis:
          image: redis:7.4
          ports: ['6379:6379']
  ```
  Then build+run the backend jar (`../backEnd/gradlew bootJar` then `java -jar ...` backgrounded, wait for `/actuator/health`) and point `proxy.conf.json`/`baseURL` at it. Start with the **mocked** approach to keep e2e green and fast; add one real-backend smoke test later.

### Step 6 — Test pyramid + naming/structure convention

**File (new):** `.plans/06-test-strategy/TESTING.md` (or promote to `backEnd/TESTING.md` once agreed). Content:

- **The pyramid for this repo:**
  - *Base — unit (fast, no Spring):* `TaxService`, `CalculationService`, any pure helper. `new X()` + Mockito. Target the bulk of tests here + all mutation coverage.
  - *Middle — slice tests:* `@WebMvcTest` for controllers (`ScenarioController`, `TaxController`, `AuthController`), `@DataJdbcTest`/`@JdbcTest` for repositories with **one** Testcontainer.
  - *Top — journey/e2e (few, slow):* `UserFlowIntegrationTest` (`@SpringBootTest`) + Playwright browser e2e. Keep to a handful.
- **Naming:**
  - Unit/slice: `<ClassUnderTest>Test.java`.
  - Integration/journey: `<Area>IntegrationTest.java` (matches existing `UserFlowIntegrationTest`, `UserRepositoryIntegrationTest`).
  - Methods: `should<ExpectedBehavior>_when<Condition>` or the existing descriptive style; use `@DisplayName` for journeys (as `UserFlowIntegrationTest` does).
- **Structure:** mirror the main package under `src/test/java`. Note: `TaxServiceTest` currently lives in `...ngnfincalc.service` while the class is in `...ngnfincalc.tax.service` — **new tests should mirror the production package** (`tax/service`, `scenarios/service`); optionally relocate `TaxServiceTest` and update PIT `targetTests`.
- **AAA:** Arrange-Act-Assert; one behavior per test; assert on **values** (money) not just non-null.
- **BigDecimal assertions:** compare with `.compareTo(...) == 0` / AssertJ `isEqualByComparingTo`, never `.equals()` (scale-sensitive) — the existing tests already do this; codify it.

### Step 7 — k6 load test for the calculation endpoint

The calc math is exercised by `POST /api/scenarios` (auth + brand context required). Provide a k6 script with SLO thresholds.

**File (new):** `loadtest/scenario-calc.js`
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BRAND = __ENV.BRAND || 'NGN';
const errorRate = new Rate('business_errors');

export const options = {
  scenarios: {
    steady: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },   // ramp up
        { duration: '2m',  target: 20 },   // steady
        { duration: '30s', target: 0 },    // ramp down
      ],
      gracefulRampDown: '10s',
    },
  },
  // SLOs — build fails if breached.
  thresholds: {
    http_req_failed:   ['rate<0.01'],                 // <1% HTTP errors
    http_req_duration: ['p(95)<300', 'p(99)<800'],    // calc + DB write budget
    business_errors:   ['rate<0.01'],
  },
};

// Log in once per VU, reuse the session cookie (Redis-backed session).
export function setup() {
  const email = `load_${Date.now()}@test.com`;
  const jar = http.cookieJar();
  const headers = { 'Content-Type': 'application/json', 'X-Brand': BRAND };

  http.post(`${BASE_URL}/api/auth/register`,
    JSON.stringify({ email, password: 'Password123!' }), { headers });
  const login = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password: 'Password123!' }), { headers });
  check(login, { 'login 2xx': (r) => r.status >= 200 && r.status < 300 });
  return { cookies: jar.cookiesForURL(BASE_URL), headers };
}

export default function (data) {
  const payload = JSON.stringify({
    brand: BRAND,
    name: `load-${__VU}-${__ITER}`,
    principal: 1000000,
    annualRate: 12.5,
    years: 30,
    monthlyContribution: 50000,
    compoundingFrequency: 'monthly',
    taxStrategy: 'progressive',
    annualIncome: 5000000,
  });

  const res = http.post(`${BASE_URL}/api/scenarios`, payload, {
    headers: { 'Content-Type': 'application/json', 'X-Brand': BRAND },
  });

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'has totalBalance': (r) => r.json('...') !== null,   // adjust to ScenarioResponse shape
  });
  errorRate.add(!ok);
  sleep(1);
}
```
> Adjust the auth endpoints/field names and the `X-Brand` header to match `AuthController` and the brand-resolution mechanism (`BrandContext`) before first run — confirm how brand is passed (header vs body `brand`). Verify `check`s against the actual `ScenarioResponse` JSON. If auth complicates the load test, add a temporary profiled `/api/scenarios/preview` (calc-only, no persist, no auth) purely for load benchmarking, or point k6 at a seeded session cookie.

**Run locally:** `k6 run -e BASE_URL=http://localhost:8080 loadtest/scenario-calc.js`

**CI (nightly, not per-PR):** new file `.github/workflows/loadtest.yml`
```yaml
name: Load Test
on:
  schedule: [{ cron: '0 3 * * 1' }]   # weekly, Monday 03:00 UTC
  workflow_dispatch:
jobs:
  k6:
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:8.4
        env: { MYSQL_ROOT_PASSWORD: root, MYSQL_DATABASE: ngncalc }
        ports: ['3306:3306']
        options: >-
          --health-cmd="mysqladmin ping" --health-interval=10s
          --health-timeout=5s --health-retries=5
      redis:
        image: redis:7.4
        ports: ['6379:6379']
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '25' }
      - uses: gradle/actions/setup-gradle@v4
      - name: Build & run backend
        working-directory: backEnd
        run: |
          ./gradlew bootJar
          java -jar build/libs/*.jar &
          for i in $(seq 1 60); do curl -fs http://localhost:8080/actuator/health && break; sleep 2; done
      - name: Run k6
        uses: grafana/setup-k6-action@v1
      - run: k6 run -e BASE_URL=http://localhost:8080 loadtest/scenario-calc.js
```
> Keep load tests **off the PR path** — they're slow and their pass/fail is environment-sensitive. Nightly/weekly + manual dispatch is the right cadence. (Gatling is a fine JVM-native alternative if the team prefers Scala/Java DSL and Gradle integration, but k6 is lighter to wire and its threshold model maps cleanly to SLOs.)

### Step 8 — Badges in README

**File:** `README.md` — add directly under the title (line 1), before the description:
```markdown
[![Backend CI](https://github.com/<owner>/<repo>/actions/workflows/backend.yml/badge.svg)](https://github.com/<owner>/<repo>/actions/workflows/backend.yml)
[![Frontend CI](https://github.com/<owner>/<repo>/actions/workflows/frontend.yml/badge.svg)](https://github.com/<owner>/<repo>/actions/workflows/frontend.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=ifeanyichukwuOtiwa-sports_NigerianFinancialCalculator_backend&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ifeanyichukwuOtiwa-sports_NigerianFinancialCalculator_backend)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=ifeanyichukwuOtiwa-sports_NigerianFinancialCalculator_backend&metric=coverage)](https://sonarcloud.io/summary/new_code?id=ifeanyichukwuOtiwa-sports_NigerianFinancialCalculator_backend)
[![codecov](https://codecov.io/gh/<owner>/<repo>/branch/develop/graph/badge.svg)](https://codecov.io/gh/<owner>/<repo>)
```
Replace `<owner>/<repo>`. The Sonar project key is already known from `build.gradle`. Drop the Codecov badge if Sonar is the sole source of truth.

---

## 6. Wiring into CI — summary of file changes

| File | Change |
|------|--------|
| `.github/workflows/backend.yml` | `fetch-depth: 0` on checkout; replace tail with `test jacocoTestReport jacocoCoverageVerification` → `sonar` (needs `SONAR_TOKEN`) → Codecov upload → `pitest` (+ artifact) → `bootJar`. |
| `.github/workflows/frontend.yml` | Split into `unit` (lint + `test:ci` coverage + Codecov + build) and `e2e` (Playwright + report artifact) jobs. |
| `.github/workflows/loadtest.yml` (new) | Weekly/dispatch k6 run against a live backend + MySQL/Redis services. |
| `backEnd/build.gradle` | Coverage gate (Step 1) + PIT plugin & config (Step 3). |
| `backEnd/.../TestcontainersConfiguration.java` | `.withReuse(true)` + pinned image tags. |
| `frontEnd/package.json` | Add `test:ci` script. |
| `loadtest/scenario-calc.js` (new) | k6 script. |
| `codecov.yml` (new, optional) | Patch/project targets + flags. |
| `README.md` | Badges. |
| `.plans/06-test-strategy/TESTING.md` (new) | Pyramid + naming convention. |
| Repo secrets | `SONAR_TOKEN`, optional `CODECOV_TOKEN`. |

---

## 7. Verification

Run each locally before pushing:
```bash
# Backend gate
cd backEnd
./gradlew test jacocoTestReport jacocoCoverageVerification
open build/reports/jacoco/test/html/index.html         # confirm math pkgs ≥ 90%

# Mutation testing
./gradlew pitest
open build/reports/pitest/index.html                   # confirm ≥ 80% killed; inspect survivors

# Sonar (needs token)
SONAR_TOKEN=xxxx ./gradlew sonar

# Frontend
cd ../frontEnd
npm run test:ci && ls coverage/lcov.info               # coverage emitted
npx playwright install --with-deps chromium
npm run test:e2e                                        # e2e green

# Load test (backend running on :8080)
k6 run -e BASE_URL=http://localhost:8080 ../loadtest/scenario-calc.js
```
CI-level verification: open a throwaway PR that deliberately (a) drops an assertion in `TaxServiceTest` → expect **PIT** to fail; (b) deletes a covered branch's test → expect **jacocoCoverageVerification** to fail; (c) breaks a frontend spec → expect **frontend unit** job red. All three failing on demand proves the gates are live.

---

## 8. Best-practice notes & pitfalls

- **Don't chase 100%.** A global 100% target rewards asserting-nothing tests on getters and DTOs. Gate **hard** only on `scenarios.service` + `tax.service`; keep a modest global floor and **ratchet** it. Exclude DTOs/config/mappers/main from the ratio.
- **Mutation score > line coverage.** A package can be 100% line-covered and 40% mutation-killed. Treat surviving mutants on money math as bugs-in-waiting; add the assertion that kills each one.
- **Mutation runtime.** PIT can be slow if scoped wide or if `targetTests` accidentally pulls in `@SpringBootTest`/Testcontainers classes. Keep `targetClasses`/`targetTests` to the two pure classes and their pure unit tests only. Never point PIT at the whole module.
- **Flaky e2e.** Prefer mocked-backend Playwright for the default CI path (deterministic). If running real backend, always wait on `/actuator/health` before starting Playwright, keep `workers: 1` in CI (already set), and upload the `playwright-report` + traces on failure (`trace: 'on-first-retry'` is already configured).
- **Testcontainers reuse** trades speed for statefulness — tests must self-clean. Reuse helps the **local** loop most; on ephemeral CI runners the win is small, so don't over-invest there. Pin image tags (`:latest` breaks reproducibility).
- **Load tests off the PR path.** They're environment-sensitive and slow; nightly/weekly + manual dispatch. SLO thresholds should reflect a realistic runner, not a laptop.
- **One PR-comment bot.** Running both Sonar and Codecov PR comments creates noise; pick one primary (Sonar here) and treat the other as a badge-only/secondary source.
- **Ratchet, don't big-bang.** Land the gate at current levels first (green), then raise thresholds in small PRs. A gate set above current coverage blocks everyone on day one and gets disabled — the worst outcome.
- **New-code focus.** Sonar's "new code" quality gate is the pragmatic lever: enforce high standards on changed lines without retro-fitting the whole codebase.

---

## 9. Effort estimate

| Step | Effort |
|------|--------|
| 1. Coverage gate + math unit tests to reach 90% | 1–1.5 days (mostly writing `CalculationServiceTest`) |
| 2. Sonar CI wiring + Codecov + secrets | 0.5 day |
| 3. PIT plugin + config + kill survivors | 0.5–1 day |
| 4. Testcontainers reuse + image pinning | 0.5 day |
| 5. Frontend unit-in-CI + Playwright-in-CI | 1 day (e2e backend/mocking is the variable) |
| 6. Pyramid/naming doc | 0.5 day |
| 7. k6 load test + nightly workflow | 0.5–1 day (auth wiring is the variable) |
| 8. Badges | 0.25 day |
| **Total** | **~5–6 days** |

---

## 10. References

- JaCoCo Gradle plugin — coverage verification: https://docs.gradle.org/current/userguide/jacoco_plugin.html
- SonarCloud + Gradle: https://docs.sonarsource.com/sonarcloud/advanced-setup/ci-based-analysis/sonarscanner-for-gradle/
- PIT (pitest) & `info.solidsoft.pitest` Gradle plugin: https://pitest.org/ , https://github.com/szpak/gradle-pitest-plugin
- Mutation testing rationale: https://pitest.org/quickstart/mutators/
- Testcontainers reuse: https://java.testcontainers.org/features/reuse/
- Angular 21 unit-test builder (`@angular/build:unit-test`, Vitest): https://angular.dev/guide/testing
- Playwright CI: https://playwright.dev/docs/ci
- Codecov Action v5: https://github.com/codecov/codecov-action
- k6 thresholds & scenarios: https://grafana.com/docs/k6/latest/using-k6/thresholds/
- Test pyramid: https://martinfowler.com/articles/practical-test-pyramid.html
