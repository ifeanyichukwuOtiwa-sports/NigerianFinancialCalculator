# Nigerian Financial Calculator

Full-stack financial planning application built for Nigerian users with an Angular frontend, a Spring Boot backend, and local MySQL and Redis infrastructure.

This project is more than a calculator UI. It is a stateful web application with authentication, scenario persistence, tax-rule execution, export workflows, and environment-aware backend design.

## Why This Project Is Worth Reviewing

- It combines product-facing financial workflows with backend concerns such as session management, data persistence, rate limiting, and export generation.
- The backend is organized by feature instead of by technical layer, which keeps auth, scenarios, tax logic, and export concerns cohesive.
- The application uses Redis-backed server-side sessions rather than pushing auth state into the client.
- Integration tests cover the main user flow from authentication through scenario creation and export.

## Core Capabilities

- Compound-interest projections with recurring contributions
- Personal income tax estimation using the application's 2026 Nigerian tax-band configuration
- Session-based authentication
- Saved financial scenarios
- PDF and CSV export
- Side-by-side scenario comparison

Tax calculations in this project are application rules, not legal advice. If tax policy changes, the configured bands and supporting documentation should be updated together.

## Architecture Overview

```text
Angular frontend
  -> calls /api endpoints through a local proxy
  -> sends brand context for scoped access

Spring Boot backend
  -> handles auth, scenarios, tax calculation, and export workflows
  -> stores HTTP session state in Redis
  -> persists users and scenarios in MySQL
  -> manages schema changes with Liquibase

Exports
  -> PDF generation for printable scenario summaries
  -> CSV generation for portable data analysis
```

## Technical Highlights

### Frontend

- Angular 21
- Signals and standalone components
- Lazy-loaded routes
- Chart.js visualizations
- ESLint with Angular template accessibility rules

### Backend

- Spring Boot 4.0.5
- Java 25
- Spring Security with server-side sessions
- Redis-backed session storage
- MySQL 8.4
- Liquibase migrations
- JUnit 5 and Testcontainers

### Operational Concerns

- Docker Compose for local infrastructure
- Spring Boot Buildpacks for container image creation
- Actuator health endpoints
- Graceful shutdown support
- Auth endpoint rate limiting

## Backend Design

The backend uses a feature-first package layout under `backEnd/src/main/java/iwo/wintech/ngnfincalc/`:

- `auth/`: registration, login, session restoration, and auth security
- `scenarios/`: scenario APIs, storage, and calculation flow
- `tax/`: tax-band configuration and PIT calculation logic
- `export/`: PDF and CSV generation
- `platform/`: security, tenancy, and runtime infrastructure
- `shared/`: common error handling and logging

That layout keeps API, service, DTO, and persistence code close to the business capability it belongs to.

## Security Model

- Authentication is session-based, not token-spread across the client.
- Session state is stored in Redis through Spring Session.
- Requests are scoped with brand context via `X-App-Brand`.
- Sensitive backend routes require authentication.
- Auth endpoints are rate limited.

Key auth endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`

## Main Application Flows

### Scenario Management

- Create a scenario
- Recalculate growth and tax outputs
- Persist the scenario for the authenticated user
- List or delete saved scenarios

### Export Flow

- Load a saved scenario
- Build export payload from stored domain data
- Render a PDF or CSV response

## Testing

The backend includes both focused and flow-oriented tests.

- `TaxServiceTest` verifies tax calculation behavior
- `UserFlowIntegrationTest` exercises registration, login, session restoration, scenario creation, export, deletion, logout, and auth rate limiting
- Testcontainers provides MySQL and Redis during integration testing

Run backend tests:

```bash
cd backEnd
./gradlew test
```

Generate backend coverage:

```bash
cd backEnd
./gradlew jacocoTestReport
```

## Repository Layout

- [`backEnd/README.md`](./backEnd/README.md): backend architecture, runtime model, API surface, and feature-first package layout
- [`frontEnd/README.md`](./frontEnd/README.md): frontend setup and application structure
- [`doc/learning_guides.md`](./doc/learning_guides.md): implementation notes and project decisions

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Node.js 24 LTS
- Java 25+

### Local Development

1. Start infrastructure:

```bash
docker compose up -d
```

2. Start the backend:

```bash
cd backEnd
./gradlew bootRun
```

3. Start the frontend:

```bash
cd frontEnd
npm install
npm start
```

Local endpoints:

- Frontend: `http://localhost:4200`
- Backend API: `http://localhost:8080`
- MySQL: `localhost:6033`
- Redis: `localhost:6380`

The frontend dev server proxies `/api` requests to the backend.

### Dockerized App Run

Build the backend image first:

```bash
cd backEnd
./gradlew bootBuildImage
```

Then start the application profile:

```bash
docker compose --profile app up --build
```

`docker compose up -d` starts only MySQL and Redis. `--profile app` adds the backend and frontend containers.

## Deployment Notes

The checked-in configuration is optimized for local development. Production deployment should externalize:

- datasource credentials
- Redis connection settings
- allowed CORS origins
- secure cookie settings
- container image publishing and runtime configuration
