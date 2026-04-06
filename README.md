# Nigerian Financial Calculator

A full-stack financial planning tool optimized for Nigerian investors, covering compound interest, Personal Income Tax (PIT) estimation (Nigeria 2026 rules), and scenario management.

## Tech Stack

### Frontend
- **Framework**: Angular 21 (v21.2.x)
- **Features**: Signals-based state, Standalone components, `inject()` pattern, SCSS (Glassmorphism UI), Chart.js
- **Testing**: Vitest (skipping frontend unit tests per requirements)
- **Linting**: ESLint with `@angular-eslint` (AXE & Accessibility compliant)

### Backend
- **Framework**: Spring Boot 4 (v4.0.5)
- **Language**: Java 25
- **Security**: Session-based auth with Spring Security + Redis
- **Database**: MySQL 8.4 (Migrations via Liquibase)
- **Testing**: JUnit 5, Testcontainers (MySQL + Redis), AssertJ, JSONAssert
- **Multi-tenancy**: Brand isolation via `X-App-Brand` header

### DevOps
- **Containerization**: Docker & Docker Compose
- **Build**: Spring Boot Buildpacks (Paketo)
- **CI/CD**: GitHub Actions (Workflows for Frontend & Backend)

---

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Node.js 22+
- Java 25+

### 1. Infrastructure (MySQL + Redis)
```bash
docker compose up -d
```

### 2. Backend
```bash
cd backEnd
./gradlew bootRun
```
API available at `http://localhost:8080`.

### 3. Frontend
```bash
cd frontEnd
npm install
npx ng serve
```
Application available at `http://localhost:4200` (proxied to backend).

---

## Features & UI
- **Auth Flow**: Secure registration and session-based login.
- **Investment Calculator**: Compound interest with monthly contributions and tax strategies.
- **Tax Calculator**: Nigeria 2026 PIT tax bands (Progressive) with gross-to-net and net-to-gross modes.
- **Scenario Management**: Save, list, and compare different investment plans.
- **Themes**: System-aware light/dark themes with time-based intensity.

---

## Development

### Running Tests (Backend)
```bash
cd backEnd
./gradlew test
```
Integration tests use **Testcontainers** to spin up real MySQL and Redis instances.

### Generating Coverage Report
```bash
cd backEnd
./gradlew jacocoTestReport
```
Report available at `backEnd/build/reports/jacoco/test/html/index.html`.

### Linting (Frontend)
```bash
cd frontEnd
npx ng lint
```

---

## Deployment
Recommended stack:
- **Render**: Frontend (Static Site) & Backend (Web Service)
- **Aiven**: MySQL 8.4
- **Upstash**: Redis 7
