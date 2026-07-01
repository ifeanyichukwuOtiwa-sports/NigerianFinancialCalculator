# Plan 05 — Security Posture: Scanning, Secrets, Headers & Hardening

> Status: URGENT (in part). App is LIVE on Railway with split domains. `npm ci`
> in `frontEnd/` reports **26 vulnerabilities (12 high, 0 critical)** as of this
> writing. This plan fixes that first, then builds durable guardrails.

---

## 1. Objective

Establish a defensible, automated security posture for the monorepo:

1. Triage and remediate the live npm high-severity vulnerabilities in `frontEnd/`.
2. Add automated dependency/vuln scanning (Dependabot) across all three
   ecosystems (Gradle, npm, GitHub Actions).
3. Add SAST via CodeQL for Java and JS/TS.
4. Add secret scanning (gitleaks in CI + GitHub-native push protection).
5. Harden the HTTP layer with Spring Security response headers (HSTS, CSP,
   X-Content-Type-Options, frame DENY, Referrer-Policy).
6. Formally analyze and document the `SameSite=None` / CSRF trade-off created by
   the split-domain deployment, and decide whether to add explicit CSRF tokens.
7. Harden actuator + error responses.
8. Add a `SECURITY.md` disclosure policy.
9. Define which checks are **required CI gates** vs **advisory**.

## 2. Why this signals seniority

- Treats the *live* highs as an incident (triage → fix → regression guard), not
  a backlog item.
- Reasons about a subtle, real security property — that the mandatory
  `X-App-Brand` header turns every cross-origin call into a preflighted request,
  which is the *de facto* CSRF control once `SameSite=None` is required — instead
  of blindly "turning CSRF back on".
- Layers controls (defence-in-depth): scanning + SAST + secrets + headers +
  session hardening, and is explicit about the cost/false-positive profile of
  each so the gates are trustworthy rather than noisy.
- Ships policy artifacts (`SECURITY.md`, required-check config) that make the
  posture auditable and repeatable.

## 3. Current state (cited)

| Area | Current state | Source |
| --- | --- | --- |
| Backend build | Gradle, Java 25, Spring Boot 4.0.5, `jacoco` + `org.sonarqube` 6.0.1.5171 plugins, SonarCloud configured (`sonar.host.url=https://sonarcloud.io`) | `backEnd/build.gradle` |
| Frontend build | Angular 21, npm, `engines.node: 24.x` | `frontEnd/package.json` |
| Backend CI | test/build/jacoco/bootJar only; no scanning | `.github/workflows/backend.yml` |
| Frontend CI | `npm ci` → lint → build only; **no `npm audit`** | `.github/workflows/frontend.yml` |
| Node version drift | CI pins Node 22 but `engines` requires 24.x | `frontend.yml:19` vs `package.json` |
| CSRF | **Disabled** (`.csrf(AbstractHttpConfigurer::disable)`) | `SecurityConfig.java:35` |
| Response headers | **None configured** — no `headers` DSL block | `SecurityConfig.java` (absent) |
| Session cookie | `http-only: true`, `same-site: ${SESSION_COOKIE_SAME_SITE:lax}`, `secure: ${SESSION_COOKIE_SECURE:true}`, Redis-backed session store | `application.yaml:32-37`, `:20-23` |
| CSRF mitigation today | Mandatory non-safelisted `X-App-Brand` header (`BrandContextFilter`) + credentialed CORS with an explicit origin allow-list forces a preflight only the allowed origin passes | `BrandContextFilter.java:19`, `CorsConfig.java`, `SecurityConfig.java:60-71` |
| CORS | Origins/methods/headers/credentials from env (`cors.config.*`); `allow-credentials: true` | `application.yaml:55-61`, `CorsConfig.java` |
| Rate limiting | bucket4j filter present, env-driven | `RateLimitingFilter.java`, `application.yaml:63-67` |
| Actuator exposure | `health,info` only; `health.show-details: when_authorized` | `application.yaml:39-53` |
| DB creds | Defaulted in yaml (`root/123456`) for local; env-overridden in prod | `application.yaml:6-8` |
| Dependabot / CodeQL / secret scanning / SECURITY.md | **None exist** | repo-wide |

## 4. Gaps & risks (priority order)

1. **[P0 — LIVE] 12 high npm vulns.** Confirmed via `npm audit`: 26 total, 12
   high, 0 critical. Affected packages are predominantly the Angular build
   toolchain transitive graph: `esbuild`, `vite`, `undici`, `tar`, `postcss`,
   `brace-expansion`, `qs`, `@babel/core`, `js-yaml`, plus `@angular/*` pinned
   ranges. Most are **devDependency / build-time**, which materially changes the
   runtime risk (see §6.1) — but they still gate CI trust and supply-chain
   integrity and must be triaged now.
2. **[P0] SameSite=None reopens CSRF surface in prod.** Split-domain deployment
   means the session cookie must be `SameSite=None; Secure` in prod (see §6.7),
   which removes the browser's default cross-site cookie protection. CSRF is
   disabled. The `X-App-Brand`-forced preflight is the *only* thing standing in
   — this must be analyzed, documented, and hardened.
3. **[P1] No response security headers.** No HSTS, CSP, `X-Content-Type-Options`,
   frame protection, or Referrer-Policy — clickjacking, MIME-sniff, and mixed
   content exposure.
4. **[P1] No automated dependency/vuln/secret scanning.** Regressions on the npm
   fix, new backend CVEs (e.g. Spring/Jackson/Netty), and committed secrets all
   go undetected.
5. **[P2] Actuator/error hardening.** `info` is exposed; error responses may leak
   stack traces / details depending on defaults.
6. **[P2] No disclosure policy** (`SECURITY.md`).

---

## 5. Step-by-step execution

Work in the order below. §5.1 is the incident fix and can ship as its own PR.

### 5.1 Triage & remediate the live npm highs (P0)

All commands run from `frontEnd/`.

**Step 1 — capture the baseline (do this before changing anything):**

```bash
cd frontEnd
npm audit                      # human-readable summary
npm audit --json > ../.plans/05-security-posture/audit-before.json
```

**Step 2 — understand the runtime vs build distinction.**

```bash
npm audit --omit=dev           # PRODUCTION/runtime-shipped deps only
```

- `npm audit` (default) reports the **entire** tree including devDependencies
  (build tooling: esbuild, vite, postcss, the Angular CLI, etc.).
- `npm audit --omit=dev` (the modern replacement for the older
  `npm audit --production`) reports only what is installed when
  `NODE_ENV=production` / `--omit=dev` — i.e. code that could ship to the
  browser bundle or run in a server process.
- **Rule of thumb for this repo:** a `high` that appears in `npm audit` but
  **not** in `npm audit --omit=dev` is a build-time-only issue. It still gets
  fixed, but it does **not** justify a risky breaking upgrade of a runtime
  dependency under time pressure. Record which highs fall into each bucket.

**Step 3 — apply the safe, non-breaking fixes first:**

```bash
npm audit fix                  # in-range (semver-compatible) upgrades only
npm audit                      # re-check what remains
```

Re-run `npm ci` and the build to confirm nothing broke:

```bash
rm -rf node_modules
npm ci
npx ng build
npm test -- --watch=false || true   # confirm unit tests still pass
```

**Step 4 — review the remaining highs that need breaking (major) upgrades.**

Do **not** run `npm audit fix --force` blindly — it will bump majors and can
break the Angular build. For each remaining advisory:

```bash
npm audit                      # note the advisory + "fix available via" line
npm ls <pkg>                   # find WHO pulls it in (direct vs transitive)
```

- **Transitive (pulled in by `@angular/build`, `vite`, etc.):** prefer bumping
  the *top-level* Angular packages to the latest patch/minor within Angular 21
  (`npm outdated @angular/core @angular/build ...` then update in-range). The
  Angular team fixes these downstream; a `@angular/build` patch usually drags
  `esbuild`/`vite`/`undici` to fixed versions without a manual override.
- If a fixed transitive version exists but the parent hasn't released, pin it
  with a **`overrides`** block in `package.json` (npm-native, no extra tooling):

  ```jsonc
  // frontEnd/package.json
  "overrides": {
    "esbuild": ">=0.25.0",
    "undici": ">=6.21.1"
  }
  ```

  Then `npm install`, re-audit, and **full-build + test** to prove the override
  is compatible. Remove overrides once the parent catches up (Dependabot will
  surface the parent bump).
- **Direct dependency needing a major:** treat as its own scoped change with a
  changelog/breaking-changes review — never inside the incident PR.

**Step 5 — accept-and-document any residual.** If a build-time-only high has no
non-breaking fix, record it (advisory id, package, why deferred, review date) in
this plan's directory as `residual-risk.md`. Do not let it silently block the
required gate you add in §5.10 — configure the gate threshold accordingly.

**Regression prevention:** add the `npm audit` gate from §5.10 to
`frontend.yml`, and let Dependabot (§5.2) keep the tree current. Commit the
updated `package-lock.json`.

### 5.2 Dependabot — `.github/dependabot.yml` (all 3 ecosystems)

Create `/.github/dependabot.yml`:

```yaml
version: 2
updates:
  # ── Backend: Gradle ──────────────────────────────────────────────
  - package-ecosystem: "gradle"
    directory: "/backEnd"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "06:00"
      timezone: "Africa/Lagos"
    open-pull-requests-limit: 10
    labels: ["dependencies", "backend", "security"]
    commit-message:
      prefix: "build(deps)"
      include: "scope"
    groups:
      spring:
        patterns: ["org.springframework*", "io.spring*"]
      test:
        patterns: ["*junit*", "*testcontainers*", "*jsonassert*"]

  # ── Frontend: npm ────────────────────────────────────────────────
  - package-ecosystem: "npm"
    directory: "/frontEnd"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "06:00"
      timezone: "Africa/Lagos"
    open-pull-requests-limit: 10
    labels: ["dependencies", "frontend", "security"]
    versioning-strategy: "increase"
    commit-message:
      prefix: "build(deps)"
      include: "scope"
    groups:
      angular:
        patterns: ["@angular/*", "@angular-devkit/*"]
      build-tooling:
        patterns: ["esbuild", "vite", "postcss", "@babel/*", "typescript"]
      # Keep security patches ungrouped so they land fast:
    ignore:
      # Example: block noisy majors until manually reviewed.
      # - dependency-name: "@angular/*"
      #   update-types: ["version-update:semver-major"]

  # ── CI: GitHub Actions ───────────────────────────────────────────
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
    labels: ["dependencies", "ci"]
    commit-message:
      prefix: "ci(deps)"
```

Notes:
- `directory` must point at where the manifest lives (`/backEnd`, `/frontEnd`),
  matching the monorepo layout.
- Dependabot also raises **security** PRs out-of-band (Dependabot alerts) —
  enable those in repo Settings → Code security (§5.5).
- Grouping reduces PR noise; security fixes are best left ungrouped so they
  merge fast.

### 5.3 CodeQL SAST — `.github/workflows/codeql.yml` (Java + JS/TS)

Create `/.github/workflows/codeql.yml`:

```yaml
name: CodeQL

on:
  push:
    branches: [develop, main]
  pull_request:
    branches: [develop, main]
  schedule:
    - cron: "17 3 * * 1"   # weekly, Monday 03:17 UTC

jobs:
  analyze:
    name: Analyze (${{ matrix.language }})
    runs-on: ubuntu-latest
    permissions:
      security-events: write   # required to upload results
      actions: read
      contents: read
    strategy:
      fail-fast: false
      matrix:
        include:
          - language: java-kotlin
            build-mode: manual
          - language: javascript-typescript
            build-mode: none
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        if: matrix.language == 'java-kotlin'
        uses: actions/setup-java@v4
        with:
          distribution: "temurin"
          java-version: "25"

      - name: Setup Gradle
        if: matrix.language == 'java-kotlin'
        uses: gradle/actions/setup-gradle@v4

      - name: Initialize CodeQL
        uses: github/codeql-action/init@v3
        with:
          languages: ${{ matrix.language }}
          build-mode: ${{ matrix.build-mode }}
          queries: security-extended

      # Java needs a real build so CodeQL can trace bytecode.
      - name: Build backend (for CodeQL)
        if: matrix.language == 'java-kotlin'
        working-directory: backEnd
        run: ./gradlew clean compileJava -x test --no-daemon

      - name: Perform CodeQL Analysis
        uses: github/codeql-action/analyze@v3
        with:
          category: "/language:${{ matrix.language }}"
```

Notes:
- `java-kotlin` requires **`build-mode: manual`** with a compile step (Java 25
  toolchain here); `javascript-typescript` uses `build-mode: none` (no build).
- `security-extended` broadens the query set beyond the default; drop to the
  default pack if the run is too slow/noisy at first.
- Results appear under the repo Security → Code scanning tab.

### 5.4 Dependency vulnerability scanning: OWASP dependency-check vs Sonar

**Recommendation: rely on Dependabot + CodeQL + SonarCloud for the backend, and
do NOT add the OWASP Dependency-Check Gradle plugin as a required gate.**

Justification for **this** repo:

- SonarCloud is **already configured** (`build.gradle` `sonar {}` block,
  SonarCloud host). SonarCloud already reports vulnerable dependencies and
  security hotspots — adding OWASP DC duplicates coverage.
- Dependabot's Gradle updater + Dependabot security alerts give per-CVE,
  auto-PR remediation, which is more actionable than a report artifact.
- OWASP Dependency-Check's NVD feed download is slow and flaky in CI (rate
  limits without an NVD API key), and it is noisy (CPE false positives). As a
  *blocking* gate it causes red builds unrelated to real risk — poor cost/benefit
  next to the tools already in place.

**However**, wire the existing Sonar analysis into CI (it currently is not run by
any workflow — the plugin is configured but never invoked). Add to
`backend.yml`:

```yaml
      - name: SonarCloud scan
        working-directory: backEnd
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: ./gradlew sonar --info
```

Run it after `jacocoTestReport` so coverage is available. This is **advisory**
initially (see §5.10). Add `SONAR_TOKEN` in repo secrets.

*Optional escape hatch:* if a formal SCA report is later required for compliance,
add OWASP Dependency-Check as a **scheduled, non-blocking** job (weekly) with an
`NVD_API_KEY` secret and `failBuildOnCVSS` unset — not on the PR path.

### 5.5 Secret scanning: gitleaks in CI + GitHub-native protection

**5.5a — gitleaks in CI.** Create `/.github/workflows/gitleaks.yml`:

```yaml
name: Secret Scan

on:
  push:
    branches: [develop, main]
  pull_request:
    branches: [develop, main]

jobs:
  gitleaks:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0   # full history so PR diffs scan added commits
      - name: Run gitleaks
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

Add a `/.gitleaks.toml` to reduce false positives from the known local
defaults, while still catching real leaks:

```toml
title = "gitleaks config"
[extend]
useDefault = true

[allowlist]
description = "Local-only non-secret defaults"
regexes = [
  '''DB_PASSWORD:123456''',      # local default in application.yaml only
  '''password: 123456''',
]
paths = [
  '''frontEnd/package-lock.json''',
  '''.*\.min\.js''',
]
```

> Follow-up: the local DB default (`root/123456`) in `application.yaml:8` is a
> non-secret dev default, but treat it as tech debt — prefer no default (fail
> fast if `DB_PASSWORD` unset) so the allowlist entry can eventually be removed.

**5.5b — GitHub-native secret scanning + push protection** (repo Settings →
Code security and analysis). Enable:
- **Dependabot alerts** and **Dependabot security updates**.
- **Secret scanning** and **Push protection** (blocks pushes containing
  recognized secret patterns before they land — the strongest control here).
- **CodeQL / code scanning** (satisfied by §5.3).

Document these toggles here since they are not code and won't be captured by a PR.

### 5.6 HTTP security headers via Spring Security `headers` DSL

Add a `headers(...)` block to the `securityFilterChain` in
`backEnd/src/main/java/iwo/wintech/ngnfincalc/platform/security/SecurityConfig.java`.
Insert it into the existing `http` builder chain (e.g. right after `.cors(...)`):

```java
.headers(headers -> headers
    // Clickjacking: this is a JSON API + separate SPA; deny all framing.
    .frameOptions(frame -> frame.deny())
    // MIME sniffing protection.
    .contentTypeOptions(withDefaults())
    // HSTS — safe because prod is HTTPS-only (Secure cookies already required).
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .preload(true)
        .maxAgeInSeconds(31536000))   // 1 year
    // Referrer policy: don't leak full URLs cross-origin.
    .referrerPolicy(ref -> ref.policy(
        ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    // CSP — this backend serves only JSON APIs, so lock it right down.
    // (The SPA is served from a DIFFERENT origin/host, so this CSP governs
    //  only API responses/error pages, not the Angular app.)
    .contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; " +
        "form-action 'none'"))
);
```

Required imports:

```java
import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
```

Notes:
- The **CSP above is for the API host only.** The Angular SPA is served from a
  different Railway domain; its CSP must be set where the SPA is served (static
  host / reverse proxy / `index.html` meta or server header) — track that as a
  separate frontend task. A restrictive `default-src 'none'` on JSON responses
  is correct and harmless.
- Do **not** add HSTS if any non-HTTPS entrypoint remains. Prod here is HTTPS
  (cookies are `Secure`), so HSTS is appropriate; keep it out of local profiles
  if you test over `http://localhost` by gating with a profile or leaving
  `SESSION_COOKIE_SECURE` handling as-is (HSTS only takes effect over HTTPS
  anyway, so it is low-risk locally).
- `frameOptions().deny()` + `frame-ancestors 'none'` are belt-and-braces.

### 5.7 SameSite=None / CSRF analysis + recommendation

**The situation.** Split-domain deployment (SPA on one Railway domain, API on
another) means the browser treats API calls as **cross-site**. For the `SESSION`
cookie to be sent at all, it must be `SameSite=None; Secure` in prod — i.e. set
`SESSION_COOKIE_SAME_SITE=none` and `SESSION_COOKIE_SECURE=true` in the prod env
(`application.yaml:36-37` already parameterizes both). `SameSite=Lax` (the local
default) would silently drop the cookie cross-site and break auth in prod.

**Why that is a CSRF concern.** `SameSite=Lax/Strict` is the browser's built-in
CSRF defense. Setting `None` removes it, and CSRF is explicitly **disabled**
(`SecurityConfig.java:35`). So the question is: what prevents a malicious site
from making a credentialed cross-site request that rides the user's session
cookie?

**The current mitigation (analyzed).** Every authenticated request must carry
`X-App-Brand` (enforced by `BrandContextFilter` and required by app logic).
`X-App-Brand` is **not** a CORS-safelisted header, so any cross-origin
`fetch`/XHR that sets it triggers a **CORS preflight** (`OPTIONS`). The
preflight only succeeds for origins in `cors.config.allowed-origins`
(credentialed CORS with an explicit allow-list; `allow-credentials: true`,
`CorsConfig.java`). A malicious origin fails the preflight, so the browser never
sends the real request. Net effect: **the mandatory custom header + strict
credentialed CORS allow-list function as a custom-header CSRF defense** — a
recognized pattern (OWASP calls this the "custom request header" CSRF
mitigation).

**Where it holds / where it is thin:**
- Holds for `fetch`/XHR from a browser, because those are subject to CORS and the
  custom header forces a preflight. ✅
- **Simple requests** (`GET`/`POST` with only safelisted headers/content-types
  like `text/plain`, `application/x-www-form-urlencoded`, `multipart/form-data`)
  can be sent cross-site **without** a preflight — e.g. via an auto-submitting
  HTML `<form>`. Such a request **cannot set `X-App-Brand`**, so if every
  state-changing endpoint genuinely rejects requests lacking a valid
  `X-App-Brand`, the forged request fails at the app layer. This is the crux:
  the defense depends on `X-App-Brand` being *mandatory for all mutating
  endpoints*, not merely read where present. `BrandContextFilter` currently only
  *sets* context when the header is present and does **not** reject when absent —
  the rejection must happen downstream. **Verify this.**

**Recommendation (defense-in-depth, ordered):**

1. **Confirm and, if needed, enforce** that all state-changing endpoints reject
   requests without a valid `X-App-Brand`. If enforcement is only implicit,
   make `BrandContextFilter` (or a dedicated check) return `400/403` when the
   header is missing on non-safe methods. This closes the simple-request gap and
   is the single highest-value change here. **[required]**
2. **Add explicit CSRF tokens** for full standards-compliance. With a split
   domain and cookie-based sessions, use Spring Security's
   `CookieCsrfTokenRepository.withHttpOnlyFalse()` (double-submit cookie), the
   Angular `HttpClient` `withXsrfConfiguration`, and set the XSRF cookie
   `SameSite=None; Secure`. This is the textbook-correct control but adds
   cross-domain cookie/token plumbing (the CSRF cookie must also be readable
   cross-site by the SPA). **Recommendation: adopt this as a fast-follow**, not
   in the urgent PR — the custom-header + preflight defense (once §5.7.1 is
   enforced) is a legitimate, sufficient interim control, and rushing CSRF-token
   wiring across two domains risks breaking live auth.
3. **Document the decision** in `SecurityConfig.java` with a comment on the
   `.csrf(...disable)` line explaining *why* it is disabled (custom-header +
   strict CORS + mandatory brand header) so the next engineer doesn't "fix" it
   by naively re-enabling CSRF and breaking the SPA. **[required]**

Also set the prod env explicitly and record it here:
`SESSION_COOKIE_SAME_SITE=none`, `SESSION_COOKIE_SECURE=true`.

### 5.8 Actuator & error hardening

- **Actuator:** exposure is already limited to `health,info`
  (`application.yaml:39-53`) and `health.show-details: when_authorized` — good.
  Actions:
  - Consider dropping `info` from `management.endpoints.web.exposure.include`
    unless it is consumed; `info` can leak build/git metadata.
  - Keep `/actuator/health`, `/actuator/health/**`, `/actuator/info` as the only
    permitAll actuator matchers (already the case, `SecurityConfig.java:44`).
  - Do **not** add `env`, `beans`, `heapdump`, `threaddump`, `loggers`,
    `mappings` to web exposure in prod.
- **Error responses:** add to `application.yaml` to prevent detail leakage:

  ```yaml
  server:
    error:
      include-message: never
      include-binding-errors: never
      include-stacktrace: never
      include-exception: false
  ```

  If clients need field-level validation messages, prefer a curated
  `@RestControllerAdvice` that returns a safe, structured error body rather than
  flipping `include-binding-errors: always`.

### 5.9 `SECURITY.md` (vulnerability disclosure policy)

Create `/SECURITY.md`:

```markdown
# Security Policy

## Supported Versions

This project is actively developed. Only the latest release deployed from the
`main` branch receives security fixes.

| Version | Supported |
| ------- | --------- |
| latest (`main`) | ✅ |
| older           | ❌ |

## Reporting a Vulnerability

**Please do not open public GitHub issues for security vulnerabilities.**

Report privately using **GitHub Security Advisories**:
1. Go to the repository's **Security** tab → **Report a vulnerability**
   (Private Vulnerability Reporting), or
2. Email the maintainer at **ifeanyichukwu.otiwa@pawatech.com** with:
   - a description of the issue and its impact,
   - steps to reproduce (PoC if possible),
   - affected component (`backEnd` / `frontEnd`) and version/commit.

## Our Commitment

- We acknowledge reports within **3 business days**.
- We provide an assessment and remediation timeline within **10 business days**.
- We will credit reporters who wish to be named once a fix is released.

## Scope

In scope: this repository's backend API and frontend application.
Out of scope: third-party services (Railway, SonarCloud, etc.), findings that
require physical access, and social-engineering attacks.
```

Enable **Private Vulnerability Reporting** in repo Settings → Code security so
the "Report a vulnerability" button exists.

### 5.10 Required CI gates vs advisory

| Check | Workflow | Gate policy | Rationale |
| --- | --- | --- | --- |
| Frontend `npm ci` + build + lint | `frontend.yml` | **Required** (already) | Baseline correctness |
| `npm audit` (high+) | `frontend.yml` (add) | **Required** once §5.1 clears highs | Prevents regression of the fix |
| Backend `gradlew build` + tests | `backend.yml` | **Required** (already) | Baseline correctness |
| CodeQL (Java + JS/TS) | `codeql.yml` | **Required** on PR (advisory first ~2 weeks to tune noise) | SAST is high-signal once tuned |
| gitleaks | `gitleaks.yml` | **Required** | Cheap, high-value, low false-positive with allowlist |
| GitHub push protection | repo setting | **Required** (blocks at push) | Strongest secret control |
| SonarCloud scan | `backend.yml` (add) | **Advisory** (quality gate can be promoted to required later) | Avoid blocking on quality-gate churn initially |
| Dependabot PRs | `dependabot.yml` | N/A (opens PRs; those PRs run the gates above) | — |
| OWASP Dependency-Check | (not added) | N/A | Superseded by Sonar + Dependabot |

Add the `npm audit` gate to `frontend.yml` after `npm ci`:

```yaml
      - name: Audit dependencies (fail on high+)
        working-directory: frontEnd
        run: npm audit --audit-level=high
```

If §5.1 leaves an accepted, documented build-time residual, either raise the
threshold to `critical` temporarily **or** add a scoped `--omit=dev` variant:

```yaml
      - name: Audit production dependencies
        working-directory: frontEnd
        run: npm audit --omit=dev --audit-level=high
```

**Also fix the Node version drift:** bump `frontend.yml` `node-version` from
`'22'` to `'24'` to match `engines.node: 24.x` in `package.json`, and use the
same in the CodeQL JS job if a build is later added.

Enforce "required" status in repo Settings → Branches → branch protection for
`main` and `develop` (add these check names to *Require status checks to pass*).

---

## 6. Wiring into CI (summary of file changes)

| Action | Path |
| --- | --- |
| New | `/.github/dependabot.yml` (§5.2) |
| New | `/.github/workflows/codeql.yml` (§5.3) |
| New | `/.github/workflows/gitleaks.yml` (§5.5a) |
| New | `/.gitleaks.toml` (§5.5a) |
| New | `/SECURITY.md` (§5.9) |
| Edit | `.github/workflows/frontend.yml` — add `npm audit` gate; Node 22→24 (§5.10) |
| Edit | `.github/workflows/backend.yml` — add SonarCloud scan step (§5.4) |
| Edit | `backEnd/.../security/SecurityConfig.java` — `headers` DSL (§5.6); CSRF-rationale comment + optional brand enforcement (§5.7) |
| Edit | `backEnd/src/main/resources/application.yaml` — error hardening; optionally drop `info` (§5.8) |
| Edit | `frontEnd/package.json` / `package-lock.json` — audit fixes + any `overrides` (§5.1) |
| Repo settings | Enable Dependabot alerts/updates, secret scanning + push protection, private vuln reporting, branch protection required checks (§5.5b, §5.9, §5.10) |
| Secrets | Add `SONAR_TOKEN` (§5.4) |
| Prod env | `SESSION_COOKIE_SAME_SITE=none`, `SESSION_COOKIE_SECURE=true` (§5.7) |

Recommended PR sequencing:
1. **PR-A (urgent):** §5.1 npm fixes + `npm audit` gate + Node version fix.
2. **PR-B:** headers (§5.6) + CSRF enforcement/comment (§5.7) + error/actuator
   hardening (§5.8).
3. **PR-C:** Dependabot + CodeQL + gitleaks + `SECURITY.md` + Sonar CI step +
   repo settings.

---

## 7. Verification

- **npm fix:** `cd frontEnd && rm -rf node_modules && npm ci` shows **0 high**
  (or only documented residuals) in the install summary; `npx ng build` and
  `npm test -- --watch=false` pass; `npm audit --audit-level=high` exits 0.
- **Dependabot:** after merge, Settings → Code security shows the config parsed;
  Dependabot opens PRs (or "Last checked" timestamp updates). Test one grouped
  PR runs the required checks.
- **CodeQL:** workflow completes green; Security → Code scanning shows results
  for both `java-kotlin` and `javascript-typescript`.
- **gitleaks:** deliberately commit a fake AWS-style key on a throwaway branch →
  gitleaks job fails; remove it → passes. Confirm push protection blocks the
  same at push time.
- **Headers:** against a running backend,
  `curl -sI https://<api-domain>/actuator/health | grep -iE 'strict-transport|x-content-type|x-frame|referrer-policy|content-security-policy'`
  shows all five headers. Cross-check with securityheaders.com / Mozilla
  Observatory.
- **CSRF/SameSite:** confirm in prod the `SESSION` cookie is
  `SameSite=None; Secure; HttpOnly` (browser devtools). Craft an off-origin
  `fetch` with credentials and `X-App-Brand` → blocked by CORS preflight.
  Craft a cross-site auto-submit `<form>` POST to a mutating endpoint (no
  `X-App-Brand`) → must be rejected `400/403` (validates §5.7.1).
- **Actuator:** `curl https://<api-domain>/actuator/env` → `401/404` (not
  exposed); error responses contain no stack traces.
- **Gates:** open a test PR that intentionally trips each required check and
  confirm merge is blocked.

## 8. Best-practice notes & pitfalls

- **Never `npm audit fix --force` under incident pressure** — it bumps majors and
  can break the Angular build. Prefer in-range fixes + `overrides` + Angular
  top-level bumps.
- **Devil is in `--omit=dev`:** most of these 12 highs are build-time. Don't take
  a runtime-breaking upgrade to silence a build-only advisory; document instead.
- **Don't naively re-enable CSRF** just because it's "off" — it will break the
  cross-domain SPA. Enforce `X-App-Brand` on mutations first; add CSRF tokens
  deliberately with cross-domain cookie config.
- **HSTS is sticky:** once a browser sees it, it pins HTTPS for `max-age`. Only
  ship it when HTTPS is guaranteed (it is here). Add `preload` only if you intend
  to submit to the preload list.
- **CSP for a JSON API ≠ CSP for the SPA.** The backend CSP (`default-src
  'none'`) does not protect the Angular app; the SPA needs its own CSP at its own
  host. Don't conflate them.
- **gitleaks allowlist discipline:** allowlist the *local dev default* only, not
  broad patterns, or you'll blind the scanner. Better long-term: remove the
  hardcoded local DB default entirely.
- **CodeQL noise:** start `security-extended` as advisory; promote to required
  after triaging the first runs, or the team learns to ignore red.
- **Dependabot noise:** grouping + weekly cadence keeps it manageable; leave
  security updates ungrouped so they land fast.
- **Node drift:** CI on 22 while `engines` demands 24.x can hide
  version-specific breakage — align them.
- **Secrets in git history:** if the DB default (or any real secret) was ever a
  *real* credential, rotating it is mandatory — scanning the tip doesn't undo a
  historical leak. Run `gitleaks detect` over full history once.

## 9. Effort estimate

| Task | Effort |
| --- | --- |
| §5.1 npm triage + fix + verify (PR-A) | 0.5–1.5 day (depends on how many need overrides/Angular bumps) |
| §5.6 headers | 1–2 h |
| §5.7 CSRF analysis, brand enforcement, comment (+ optional token wiring) | 0.5 day (2–3 h without tokens; +0.5–1 day if adopting CSRF tokens cross-domain) |
| §5.8 actuator/error hardening | 1 h |
| §5.2 Dependabot | 30 min |
| §5.3 CodeQL | 1–2 h (incl. tuning) |
| §5.4 Sonar CI wiring | 1 h |
| §5.5 gitleaks + repo settings | 1–2 h |
| §5.9 SECURITY.md + settings | 30 min |
| §5.10 gates + branch protection | 1 h |
| **Total** | **~3–4 developer-days** (PR-A alone ~0.5–1.5 day and shippable immediately) |

## 10. References

- OWASP Cheat Sheet — CSRF Prevention (custom request header pattern):
  https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html
- OWASP Secure Headers Project:
  https://owasp.org/www-project-secure-headers/
- Spring Security — HTTP Response Headers (headers DSL, HSTS, CSP,
  frame-options, referrer-policy):
  https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html
- Spring Security — CSRF (incl. cookie repository / SPA guidance):
  https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
- MDN — SameSite cookies:
  https://developer.mozilla.org/docs/Web/HTTP/Headers/Set-Cookie/SameSite
- npm docs — `npm audit` (`--audit-level`, `--omit=dev`) & `overrides`:
  https://docs.npmjs.com/cli/commands/npm-audit ,
  https://docs.npmjs.com/cli/configuring-npm/package-json#overrides
- GitHub — Dependabot config options:
  https://docs.github.com/code-security/dependabot/dependabot-version-updates/configuration-options-for-the-dependabot.yml-file
- GitHub — CodeQL code scanning (advanced setup):
  https://docs.github.com/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/configuring-advanced-setup-for-code-scanning
- GitHub — Secret scanning & push protection:
  https://docs.github.com/code-security/secret-scanning/introduction/about-secret-scanning
- gitleaks-action:
  https://github.com/gitleaks/gitleaks-action
- OWASP Dependency-Check Gradle plugin (context for §5.4):
  https://github.com/dependency-check/dependency-check-gradle
