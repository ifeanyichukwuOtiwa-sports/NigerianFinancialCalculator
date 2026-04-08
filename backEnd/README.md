# Backend

Spring Boot backend for the Nigerian Financial Calculator.

## Stack

- Spring Boot 4.0.5
- Java 25
- MySQL 8.4
- Redis-backed HTTP sessions
- Liquibase migrations
- JUnit 5 and Testcontainers

## Code Organization

The backend now uses a feature-first package layout instead of a layer-first layout.

Top-level packages under `src/main/java/iwo/wintech/ngnfincalc/`:

- `auth/`: authentication, auth DTOs, auth repository, auth security
- `scenarios/`: scenario APIs, scenario persistence, calculation flow
- `tax/`: tax APIs, tax config mapping, tax repository, PIT logic
- `export/`: PDF and CSV export orchestration and renderers
- `platform/`: cross-cutting runtime concerns such as security and tenancy
- `shared/`: shared error handling and logging

Examples:

- `auth/web/AuthController`
- `auth/service/AuthService`
- `scenarios/web/ScenarioController`
- `scenarios/service/ScenarioService`
- `tax/web/TaxController`
- `export/service/ExportService`
- `platform/security/SecurityConfig`
- `platform/tenancy/TenantContext`
- `shared/error/GlobalExceptionHandler`

When adding new backend code, prefer placing it inside the owning feature package first. Only use `platform` or `shared` when the concern is genuinely cross-cutting.

## Run Locally

Start infrastructure from the repository root:

```bash
docker compose up -d
```

Then run the backend:

```bash
./gradlew bootRun
```

The API starts on `http://localhost:8080`.

## Runtime Dependencies

The default profile is `local`. Local development uses `src/main/resources/application-local.yaml`.

Local defaults expect:

- MySQL on `localhost:6033`
- Redis on `localhost:6380`
- CORS allowed origin `http://localhost:4200`

Cloud and container deployments should provide configuration through environment variables such as:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `CORS_ALLOWED_ORIGINS`
- `SESSION_COOKIE_SECURE`
- `SERVER_PORT`

For containerized startup, build the backend image first:

```bash
./gradlew bootBuildImage
```

Then from the repository root:

```bash
docker compose --profile app up --build
```

## Health and Lifecycle

The backend now exposes actuator probe endpoints for orchestrated environments:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /actuator/info`

Graceful shutdown is enabled so in-flight requests can complete during platform restarts.

## Auth and Session Model

- `/api/auth/register` creates a user within the current brand context
- `/api/auth/login` authenticates and creates a server-side session
- `/api/auth/me` returns the current authenticated user from the session
- `/api/auth/logout` invalidates the session and removes the `SESSION` cookie

Session state is stored in Redis through Spring Session.

## Brand Context

The backend expects an `X-App-Brand` header on requests. The frontend sends this automatically through an HTTP interceptor.

Brand context is used to scope user and scenario access. If you call the API outside the frontend, include a header such as:

```http
X-App-Brand: NGN
```

## Main API Areas

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/scenarios`
- `GET /api/scenarios`
- `DELETE /api/scenarios/{id}`
- `GET /api/scenarios/{id}/export/pdf`
- `GET /api/scenarios/{id}/export/csv`

The package layout mirrors those backend capabilities so API, service, DTO, and repository changes for one feature stay close together.

## Tests

Run all tests:

```bash
./gradlew test
```

Generate coverage:

```bash
./gradlew jacocoTestReport
```

The integration flow exercises:

- unauthorized access handling
- registration and login
- session restoration endpoint
- scenario creation, listing, deletion
- PDF and CSV export
- auth rate limiting

## Notes

- Liquibase creates the schema on startup.
- Production deployments should override datasource, Redis, CORS, and cookie-security settings.
- Tax and scenario export behavior is implemented in application code and should be versioned with its docs.
- Backend code should be organized by feature, not by technical layer, unless the concern is truly shared infrastructure.
