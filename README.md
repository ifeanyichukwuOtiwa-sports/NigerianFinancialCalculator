# Nigerian Financial Calculator

A full-stack financial calculator for Nigerian users. The repository contains an Angular frontend, a Spring Boot backend, and local infrastructure for MySQL and Redis.

## What It Does

- Compound-interest projections with recurring contributions
- Personal income tax estimation using the app's 2026 Nigeria tax-band configuration
- Session-based authentication
- Saved scenarios with PDF and CSV export
- Side-by-side scenario comparison

Tax calculations in this project are application rules, not legal advice. If the tax policy changes, update the configured bands and supporting docs together.

## Stack

### Frontend
- Angular 21
- Signals and standalone components
- Angular Router with lazy-loaded pages
- Chart.js
- ESLint with Angular template accessibility rules

### Backend
- Spring Boot 4.0.5
- Java 25 toolchain
- Spring Security with server-side sessions
- Redis-backed session storage
- MySQL 8.4 with Liquibase migrations
- JUnit 5 and Testcontainers

### Local Infrastructure
- Docker Compose
- Spring Boot Buildpacks for the backend container image

## Repository Docs

- [`frontEnd/README.md`](./frontEnd/README.md): frontend setup and architecture
- [`backEnd/README.md`](./backEnd/README.md): backend setup, runtime model, API surface, and feature-first package layout
- [`doc/learning_guides.md`](./doc/learning_guides.md): implementation notes and project decisions

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Node.js 22+
- Java 25+

### Local Development With Host-Run App

1. Start infrastructure only:
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

Then start the app profile:

```bash
docker compose --profile app up --build
```

`docker compose up -d` starts only MySQL and Redis. `--profile app` adds the backend and frontend containers.

## Development

### Backend tests
```bash
cd backEnd
./gradlew test
```

### Backend coverage
```bash
cd backEnd
./gradlew jacocoTestReport
```

Coverage report:
`backEnd/build/reports/jacoco/test/html/index.html`

### Frontend lint
```bash
cd frontEnd
npm run lint
```

### Frontend test
```bash
cd frontEnd
npm test
```

## Deployment Notes

The checked-in configuration is optimized for local development. Production deployment should externalize:

- datasource credentials
- Redis connection settings
- allowed CORS origins
- secure cookie settings
- container image publishing and runtime configuration
