# Plan 01 — Harden & Extend CI/CD (GitHub Actions) + Automated Deploy to Railway

## Objective

Turn the two thin, build-only workflows into a hardened, security-conscious CI/CD
pipeline that:

1. Runs the **full test suite** on both services (backend already does; frontend
   currently runs none).
2. Feeds **coverage + static analysis to SonarCloud** (already configured in
   `backEnd/build.gradle` but never invoked by CI).
3. Aligns CI runtimes with the versions the repo actually declares (frontend CI
   pins Node 22, but `frontEnd/package.json` `engines.node` = `24.x`).
4. Builds & publishes **Docker images** for both services.
5. **Automatically deploys** to Railway on merge to `main`, using **split
   frontend/backend services**, gated behind a protected GitHub Environment.
6. Adopts GitHub Actions best practice: least-privilege `permissions:`,
   SHA-pinned actions, `concurrency` with cancel-in-progress, path filters,
   dependency caching, and status badges.

## Why it signals seniority

- **Tests as a merge gate, not a suggestion.** A senior engineer never ships a
  frontend pipeline that builds but runs zero tests. Wiring `ng test` (unit) and
  Playwright (e2e) into required checks is the difference between "green =
  compiles" and "green = works".
- **Supply-chain hygiene.** Pinning actions to commit SHAs, scoping `GITHUB_TOKEN`
  permissions to least privilege, and isolating deploy secrets in a protected
  Environment are exactly the controls audited in real security reviews.
- **Reproducible runtimes.** Making CI use the same Node/Java toolchain the app
  declares removes the classic "works in CI, breaks in prod" class of bugs.
- **Quality gates with teeth.** SonarCloud coverage + quality-gate enforcement on
  PRs demonstrates ownership of long-term maintainability, not just "does it run".
- **Progressive delivery discipline.** A separate, environment-gated deploy
  workflow (manual approval, cancel-in-progress) shows understanding of
  deployment risk, not just `git push`.

## Current state in this repo

Two workflows exist, both build-only and both triggered on push/PR to
`develop` and `main` with path filters already in place.

### `.github/workflows/backend.yml` (existing)
- `actions/checkout@v4`, `actions/setup-java@v4` (temurin, Java 25),
  `gradle/actions/setup-gradle@v4`.
- Steps: `./gradlew clean build --parallel`, then `./gradlew jacocoTestReport`,
  then `./gradlew bootJar`.
- **Gap:** JaCoCo XML is generated but never uploaded or gated; SonarCloud is
  configured in `build.gradle` (`sonar { ... }` block, org
  `ifeanyichukwuotiwa-sports`, projectKey
  `ifeanyichukwuOtiwa-sports_NigerianFinancialCalculator_backend`) but **no
  `sonar` task is ever run**; no `permissions:`, `concurrency`, or action
  SHA-pinning; no Docker build.

### `.github/workflows/frontend.yml` (existing)
- `actions/checkout@v4`, `actions/setup-node@v4` (**node 22**, npm cache keyed on
  `frontEnd/package-lock.json`).
- Steps: `npm ci`, `npx ng lint`, `npx ng build`.
- **Gap:** Node version mismatch (CI 22 vs `engines.node: "24.x"`); **no tests
  run at all** despite `test` (`@angular/build:unit-test`) and `test:e2e`
  (Playwright) scripts existing; no coverage; no `permissions:`, `concurrency`,
  or SHA-pinning; no Docker build.

### Supporting facts verified in the repo
- `frontEnd/package.json`: `packageManager: npm@11.11.1`, `engines.node: 24.x`,
  scripts `test` (`ng test`), `test:e2e` (`playwright test`), `lint`, `husky`
  `prepare`. Dev deps include `@angular/build`, `@playwright/test`, `vitest`,
  `jsdom`, `eslint 8.57.1` (flat config via `@angular-eslint` 21).
- `frontEnd/angular.json`: `test` target uses builder
  `@angular/build:unit-test` (Vitest-based, headless by default in CI); build
  output path is `dist/front-end/browser` (confirmed by
  `frontEnd/Dockerfile` line 25).
- `frontEnd/playwright.config.ts`: `testDir: ./e2e`, `webServer.command:
  npm start ...` auto-starts the dev server, `workers: 1` under CI,
  `reuseExistingServer: !CI`, chromium only, headless. e2e spec exists at
  `frontEnd/e2e/smoke.spec.ts`.
- `backEnd/build.gradle`: Spring Boot 4.0.5, Java 25 toolchain, jacoco 0.8.14,
  sonarqube 6.0.1.5171, `bootBuildImage` (Paketo) configured, MySQL + Liquibase.
- `backEnd/src/test/java/iwo/wintech/ngnfincalc/TestcontainersConfiguration.java`:
  spins up **MySQL** (`MySQLContainer`) and **Redis** (`GenericContainer`,
  `@ServiceConnection`) — CI runners must have Docker (ubuntu-latest does).
- Dockerfiles: `backEnd/Dockerfile` (Gradle build -> temurin 25 JRE),
  `frontEnd/Dockerfile` (node 24.14.1 build -> nginx), `backEnd/Dockerfile.manual`.
- `README.md`: no status badges currently.
- Deploy target: **Railway**, split frontend + backend services.

## Gaps (confirmed against files)

| # | Gap | Evidence |
|---|-----|----------|
| 1 | Frontend CI Node 22 ≠ declared `engines.node: 24.x` | `frontend.yml:19`, `package.json:18` |
| 2 | Frontend runs no unit tests | `frontend.yml` has no `ng test` |
| 3 | Frontend runs no e2e tests | `frontend.yml` has no `playwright test` |
| 4 | SonarCloud configured but never invoked in CI | `build.gradle:92-100`, `backend.yml` has no `sonar` step |
| 5 | JaCoCo XML never uploaded/gated | `backend.yml:29-31` generates, nothing consumes |
| 6 | No Docker build/publish for either service | no image step in either workflow |
| 7 | No automated Railway deploy | no deploy workflow exists |
| 8 | No `concurrency` cancel-in-progress | absent in both workflows |
| 9 | No least-privilege `permissions:` block | absent in both workflows |
| 10 | Actions not SHA-pinned (`@v4` tags) | `backend.yml`, `frontend.yml` |
| 11 | No branch-protection / required-checks guidance | repo settings |
| 12 | No status badges | `README.md` |

---

## Step-by-step implementation

> **Global conventions used below**
> - All third-party actions are **pinned to a commit SHA** with the human-readable
>   tag in a trailing comment. **Before committing, resolve each SHA** with e.g.
>   `gh api repos/actions/checkout/git/refs/tags/v4.2.2 --jq .object.sha`
>   (or view the action's releases page). The SHAs below are placeholders of the
>   form `<sha-...>` — **do not merge until they are real 40-char SHAs**.
> - Every workflow declares a top-level least-privilege `permissions:` block and a
>   `concurrency` group.

### Step 1 — Replace `.github/workflows/frontend.yml`

Fixes Node version (24), adds unit tests (Vitest via `@angular/build:unit-test`)
and Playwright e2e, uploads coverage and Playwright report artifacts.

**File:** `.github/workflows/frontend.yml` (full replacement)

```yaml
name: Frontend CI

on:
  push:
    branches: [develop, main]
    paths: ['frontEnd/**', '.github/workflows/frontend.yml']
  pull_request:
    branches: [develop, main]
    paths: ['frontEnd/**', '.github/workflows/frontend.yml']

# Least privilege: CI only needs to read the repo.
permissions:
  contents: read

# Cancel superseded runs on the same ref (except on protected branches keep last).
concurrency:
  group: frontend-ci-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

defaults:
  run:
    working-directory: frontEnd

jobs:
  lint-test-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@<sha-checkout> # v4.2.2

      - uses: actions/setup-node@<sha-setup-node> # v4.1.0
        with:
          node-version: '24'
          cache: 'npm'
          cache-dependency-path: frontEnd/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Lint
        run: npm run lint

      - name: Unit tests (headless)
        # @angular/build:unit-test runs Vitest headless in CI by default.
        run: npx ng test --watch=false

      - name: Build (production)
        run: npx ng build --configuration=production

      - name: Upload build artifact
        if: success()
        uses: actions/upload-artifact@<sha-upload-artifact> # v4.4.3
        with:
          name: frontend-dist
          path: frontEnd/dist/front-end/browser
          retention-days: 7

  e2e:
    runs-on: ubuntu-latest
    needs: lint-test-build
    steps:
      - uses: actions/checkout@<sha-checkout> # v4.2.2

      - uses: actions/setup-node@<sha-setup-node> # v4.1.0
        with:
          node-version: '24'
          cache: 'npm'
          cache-dependency-path: frontEnd/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Install Playwright browsers
        # playwright.config.ts uses chromium only; --with-deps installs OS libs.
        run: npx playwright install --with-deps chromium

      - name: Run Playwright e2e
        # webServer in playwright.config.ts auto-starts `npm start`; workers=1 under CI.
        run: npm run test:e2e

      - name: Upload Playwright report
        if: ${{ !cancelled() }}
        uses: actions/upload-artifact@<sha-upload-artifact> # v4.4.3
        with:
          name: playwright-report
          path: frontEnd/playwright-report
          retention-days: 7
```

> **Notes**
> - `--watch=false` is the safe explicit flag for the Angular unit-test builder in
>   CI even though CI defaults to non-watch; keep it to be unambiguous.
> - e2e is a **separate job** (`needs: lint-test-build`) so a lint/unit failure
>   fails fast without paying for the browser install.
> - To emit frontend coverage for Sonar later, add `--coverage` support to the
>   unit-test builder (Vitest supports it) and wire a frontend Sonar project; out
>   of scope for v1 but noted under "Future".

### Step 2 — Replace `.github/workflows/backend.yml`

Keeps the Gradle build + JaCoCo, **adds the SonarCloud analysis step**, uploads
the JaCoCo XML as an artifact, and documents the Testcontainers requirement.

**File:** `.github/workflows/backend.yml` (full replacement)

```yaml
name: Backend CI

on:
  push:
    branches: [develop, main]
    paths: ['backEnd/**', '.github/workflows/backend.yml']
  pull_request:
    branches: [develop, main]
    paths: ['backEnd/**', '.github/workflows/backend.yml']

permissions:
  contents: read

concurrency:
  group: backend-ci-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

defaults:
  run:
    working-directory: backEnd

jobs:
  build-test-analyze:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@<sha-checkout> # v4.2.2
        with:
          # Sonar needs full history + PR metadata for accurate new-code analysis.
          fetch-depth: 0

      - uses: actions/setup-java@<sha-setup-java> # v4.5.0
        with:
          distribution: 'temurin'
          java-version: '25'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@<sha-setup-gradle> # v4.2.1

      # Testcontainers (MySQL + Redis via TestcontainersConfiguration.java) needs
      # Docker. ubuntu-latest ships with Docker preinstalled, so tests run as-is.
      - name: Build, test & JaCoCo report
        run: ./gradlew clean build jacocoTestReport --parallel

      - name: SonarCloud analysis
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }} # enables PR decoration
        run: ./gradlew sonar --info

      - name: Build bootJar
        run: ./gradlew bootJar

      - name: Upload JaCoCo XML
        if: ${{ !cancelled() }}
        uses: actions/upload-artifact@<sha-upload-artifact> # v4.4.3
        with:
          name: jacoco-report
          path: backEnd/build/reports/jacoco/test/jacocoTestReport.xml
          retention-days: 7

      - name: Upload bootJar
        if: success()
        uses: actions/upload-artifact@<sha-upload-artifact> # v4.4.3
        with:
          name: backend-jar
          path: backEnd/build/libs/*.jar
          retention-days: 7
```

> **Notes**
> - `sonar` runs **after** `jacocoTestReport`; the report path is already declared
>   in `build.gradle` (`sonar.coverage.jacoco.xmlReportPaths`). No build.gradle
>   change is required.
> - `SONAR_TOKEN` must be a repo secret (see Wiring). `GITHUB_TOKEN` is
>   auto-provided; passing it lets Sonar decorate PRs.
> - The **SonarCloud Quality Gate** should be enforced as a required status check
>   (configured in SonarCloud project settings + GitHub branch protection), not
>   via `gradlew` failing — the Sonar GitHub App reports a `SonarCloud Code
>   Analysis` check that branch protection can require. Optionally add a
>   `sonarqube-quality-gate-action` step if you want the CI job itself to hard-fail
>   on gate failure.

### Step 3 — New deploy workflow (Railway, split services, gated on `main`)

Two independent deploy jobs (frontend + backend) using the **Railway CLI**
(`railway up`), each targeting its own Railway service. Gated behind a protected
GitHub **Environment** (`production`) so a reviewer must approve. Path filters so
a frontend-only change does not redeploy the backend.

**File:** `.github/workflows/deploy.yml` (new)

```yaml
name: Deploy (Railway)

on:
  push:
    branches: [main]
    paths:
      - 'frontEnd/**'
      - 'backEnd/**'
      - '.github/workflows/deploy.yml'
  workflow_dispatch: {} # allow manual redeploy

permissions:
  contents: read

concurrency:
  # Never run two deploys of the same service concurrently; do NOT cancel an
  # in-flight deploy.
  group: deploy-${{ github.ref }}
  cancel-in-progress: false

jobs:
  changes:
    runs-on: ubuntu-latest
    outputs:
      backend: ${{ steps.filter.outputs.backend }}
      frontend: ${{ steps.filter.outputs.frontend }}
    steps:
      - uses: actions/checkout@<sha-checkout> # v4.2.2
      - uses: dorny/paths-filter@<sha-paths-filter> # v3.0.2
        id: filter
        with:
          filters: |
            backend:
              - 'backEnd/**'
            frontend:
              - 'frontEnd/**'

  deploy-backend:
    needs: changes
    if: ${{ needs.changes.outputs.backend == 'true' || github.event_name == 'workflow_dispatch' }}
    runs-on: ubuntu-latest
    environment:
      name: production
      url: ${{ vars.BACKEND_PUBLIC_URL }}
    steps:
      - uses: actions/checkout@<sha-checkout> # v4.2.2
      - name: Install Railway CLI
        run: npm i -g @railway/cli
      - name: Deploy backend service
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
        run: |
          railway up \
            --service "${{ vars.RAILWAY_BACKEND_SERVICE }}" \
            --path-as-root backEnd \
            --ci

  deploy-frontend:
    needs: changes
    if: ${{ needs.changes.outputs.frontend == 'true' || github.event_name == 'workflow_dispatch' }}
    runs-on: ubuntu-latest
    environment:
      name: production
      url: ${{ vars.FRONTEND_PUBLIC_URL }}
    steps:
      - uses: actions/checkout@<sha-checkout> # v4.2.2
      - name: Install Railway CLI
        run: npm i -g @railway/cli
      - name: Deploy frontend service
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
        run: |
          railway up \
            --service "${{ vars.RAILWAY_FRONTEND_SERVICE }}" \
            --path-as-root frontEnd \
            --ci
```

> **How Railway builds each service**
> - With `--path-as-root backEnd` / `frontEnd`, Railway uses that subdirectory as
>   the build context and will detect and use the existing `Dockerfile` in each
>   (`backEnd/Dockerfile`, `frontEnd/Dockerfile`). No Nixpacks config needed.
> - The frontend Dockerfile listens on `$PORT` (defaults 8080) via nginx envsubst;
>   the backend Dockerfile honors `--server.port=${PORT}`. Both are Railway-ready.
> - `RAILWAY_TOKEN` should be a **project token** (scoped to the one Railway
>   project) rather than an account token.
> - `--ci` makes `railway up` stream logs and exit non-zero on build/deploy
>   failure, so the GitHub job accurately reflects deploy status.
>
> **Alternative (no CLI):** connect each Railway service to this GitHub repo via
> Railway's native GitHub integration with root directory + branch = `main`. That
> removes this workflow entirely but also removes the GitHub-side approval gate
> and required-check ordering, so the CLI-in-Actions approach above is preferred
> for auditability. Pick one; do not run both (double deploys).

### Step 4 — (Optional but recommended) Docker image publish to GHCR

If you want images independent of Railway's internal build (for rollbacks /
scanning), add a build-and-push job. Keep it separate from deploy.

**File:** `.github/workflows/docker-publish.yml` (new, optional)

```yaml
name: Docker Publish (GHCR)

on:
  push:
    branches: [main]
    paths: ['backEnd/**', 'frontEnd/**', '.github/workflows/docker-publish.yml']

permissions:
  contents: read
  packages: write # push to GHCR

concurrency:
  group: docker-publish-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        include:
          - service: backend
            context: backEnd
          - service: frontend
            context: frontEnd
    steps:
      - uses: actions/checkout@<sha-checkout> # v4.2.2
      - uses: docker/setup-buildx-action@<sha-buildx> # v3.7.1
      - uses: docker/login-action@<sha-login> # v3.3.0
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/metadata-action@<sha-metadata> # v5.6.1
        id: meta
        with:
          images: ghcr.io/${{ github.repository }}/${{ matrix.service }}
          tags: |
            type=sha
            type=raw,value=latest,enable={{is_default_branch}}
      - uses: docker/build-push-action@<sha-build-push> # v6.9.0
        with:
          context: ${{ matrix.context }}
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

---

## Wiring

### GitHub repository secrets (`Settings -> Secrets and variables -> Actions -> Secrets`)
| Secret | Used by | Notes |
|--------|---------|-------|
| `SONAR_TOKEN` | backend.yml | SonarCloud user/project token, org `ifeanyichukwuotiwa-sports` |
| `RAILWAY_TOKEN` | deploy.yml | Railway **project** token (both services live in the same project) |
| `GITHUB_TOKEN` | backend.yml, docker-publish.yml | Auto-provided; only needs `packages: write` for GHCR (already scoped in workflow) |

### GitHub repository variables (`... -> Variables`)
| Variable | Value |
|----------|-------|
| `RAILWAY_BACKEND_SERVICE` | Railway service name for the backend |
| `RAILWAY_FRONTEND_SERVICE` | Railway service name for the frontend |
| `BACKEND_PUBLIC_URL` | Public backend URL (for environment deployment link) |
| `FRONTEND_PUBLIC_URL` | Public frontend URL |

### GitHub Environment (`Settings -> Environments`)
- Create environment **`production`**.
- Add **Required reviewers** (at least yourself) so deploys pause for approval.
- Optionally restrict deployment branches to `main` only.
- Move `RAILWAY_TOKEN` to an **environment secret** (instead of repo secret) so it
  is only exposed to jobs referencing `environment: production` — tighter blast
  radius.

### Branch protection (`Settings -> Branches -> add rule` for `main` and `develop`)
Require these status checks to pass before merge:
- `Frontend CI / lint-test-build`
- `Frontend CI / e2e`
- `Backend CI / build-test-analyze`
- `SonarCloud Code Analysis` (reported by the SonarCloud GitHub App)

Also enable: "Require branches to be up to date", "Require a pull request before
merging", and "Require conversation resolution".

> Note: path-filtered workflows do not run when unrelated paths change, which can
> make a required check "pending" forever on such PRs. If this bites, either drop
> the path filter on PR triggers or add a lightweight "always-green" gate job per
> workflow that runs unconditionally. Decide during rollout.

### README status badges
Add near the top of `README.md` (replace `OWNER/REPO`):

```markdown
[![Backend CI](https://github.com/OWNER/REPO/actions/workflows/backend.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/backend.yml)
[![Frontend CI](https://github.com/OWNER/REPO/actions/workflows/frontend.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/frontend.yml)
[![Deploy](https://github.com/OWNER/REPO/actions/workflows/deploy.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/deploy.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=ifeanyichukwuOtiwa-sports_NigerianFinancialCalculator_backend&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ifeanyichukwuOtiwa-sports_NigerianFinancialCalculator_backend)
```

---

## Verification steps

1. **Resolve SHAs.** Replace every `<sha-...>` placeholder with a real commit SHA
   (`gh api repos/<owner>/<action>/git/refs/tags/<tag> --jq .object.sha`).
2. **Lint the YAML locally** before pushing:
   `npx --yes @action-validator/cli .github/workflows/*.yml` (or `actionlint`).
3. **Frontend dry-run locally** to confirm the CI commands work with Node 24:
   - `cd frontEnd && nvm use 24 && npm ci && npm run lint && npx ng test --watch=false && npx ng build --configuration=production`
   - `npx playwright install --with-deps chromium && npm run test:e2e`
4. **Backend dry-run locally** (Docker must be running for Testcontainers):
   - `cd backEnd && ./gradlew clean build jacocoTestReport && SONAR_TOKEN=... ./gradlew sonar --info`
   - Confirm the run appears in SonarCloud with coverage > 0%.
5. **Open a test PR** touching only `frontEnd/**`; confirm Frontend CI runs, both
   jobs go green, artifacts (dist, playwright-report) appear, and Backend CI does
   **not** run. Repeat with a `backEnd/**`-only PR.
6. **Merge to `main`**; confirm the Deploy workflow **pauses for approval**
   (production environment), then after approval `railway up` deploys only the
   changed service(s) and the environment URL is populated.
7. **Confirm branch protection** blocks merging a PR while any required check is
   red (temporarily break a test to prove it).
8. **Confirm badges render** in `README.md` on GitHub.

---

## Best-practice notes

- **Least privilege `permissions:`** — every workflow sets top-level
  `permissions: contents: read`; only `docker-publish.yml` elevates to
  `packages: write`. Never leave the default (write-all) token.
- **Pinned action SHAs** — all `uses:` reference a 40-char commit SHA with the tag
  in a comment. Prevents a compromised/retagged action from silently running.
  Consider Dependabot (`.github/dependabot.yml`, `package-ecosystem:
  github-actions`) to bump SHAs safely.
- **`concurrency` cancel-in-progress** — CI cancels superseded PR runs
  (`cancel-in-progress: true` for PRs) to save minutes; the **deploy** workflow
  uses `cancel-in-progress: false` so a half-finished deploy is never killed.
- **Path filters** — both CI workflows and the deploy jobs are scoped by
  `paths:`/`paths-filter` so unrelated changes don't trigger irrelevant work or
  redeploys.
- **Caching** — npm cache via `setup-node`, Gradle cache via
  `gradle/actions/setup-gradle` (automatic), Docker layer cache via GHA
  (`cache-from/to: type=gha`).
- **Environments for deploy approval** — the `production` environment adds a manual
  gate + scopes the Railway token, giving an audit trail of who approved each
  release.
- **Fail-fast layering** — e2e depends on unit/lint; deploy depends on a merge to
  `main` that already passed required CI checks via branch protection.
- **No build.gradle changes required** — Sonar properties and JaCoCo XML path are
  already declared; CI only needs to invoke the existing `sonar` task.

---

## Effort estimate

| Task | Estimate |
|------|----------|
| Rewrite `frontend.yml` (node 24, unit + e2e, artifacts) | 1.5 h |
| Rewrite `backend.yml` (Sonar + coverage upload) | 1 h |
| Create `deploy.yml` (Railway split services + environment gate) | 2 h |
| Railway project/service + token setup, first successful deploy | 1.5 h |
| SonarCloud token + quality gate + PR decoration wiring | 1 h |
| Branch protection + required checks + secrets/variables | 0.5 h |
| README badges | 0.25 h |
| Resolve/pin action SHAs + actionlint | 0.5 h |
| (Optional) `docker-publish.yml` to GHCR | 1 h |
| Verification (test PRs, approval flow, red-check gate) | 1.5 h |
| **Total** | **~9.5 h core (+1 h optional GHCR)** |

---

## References

- Existing workflows: `.github/workflows/backend.yml`, `.github/workflows/frontend.yml`
- Backend build & Sonar config: `backEnd/build.gradle` (lines 5-6, 81-100)
- Testcontainers (MySQL + Redis): `backEnd/src/test/java/iwo/wintech/ngnfincalc/TestcontainersConfiguration.java`
- Frontend runtime & scripts: `frontEnd/package.json` (engines/scripts)
- Frontend test builder & output path: `frontEnd/angular.json` (`test` target, `dist/front-end/browser`)
- Playwright config: `frontEnd/playwright.config.ts` (e2e at `frontEnd/e2e/smoke.spec.ts`)
- Dockerfiles: `backEnd/Dockerfile`, `frontEnd/Dockerfile`, `backEnd/Dockerfile.manual`
- GitHub: securing use of third-party actions (pin to full SHA), least-privilege `GITHUB_TOKEN`, deployment environments & required reviewers, `concurrency`
- Railway: CLI `railway up` with `--service` / `--path-as-root` / `--ci`, project tokens, GitHub integration
- SonarCloud: Gradle scanner, JaCoCo coverage import, GitHub App PR decoration + quality gate as required check
