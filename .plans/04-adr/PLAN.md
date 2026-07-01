# Implementation Plan: Architecture Decision Records (ADRs)

> Repo: `nigerian-financial-calculator` (Spring Boot 4.0.5, Java 25 backend; Angular frontend)
> Author of plan: architecture handoff. Executor: any developer.
> Scope: introduce an ADR process **and** backfill the significant decisions already baked into this codebase.

---

## 1. Objective

Establish a lightweight, in-repo Architecture Decision Record process and retroactively document the
architecturally significant decisions that already exist in the code but live only in commit messages,
inline comments, and the heads of the original authors.

Concretely, after this plan is executed the repo will contain:

- `docs/adr/` directory with a `README.md` index and a `0000-adr-template.md`.
- A defined numbering scheme and a status lifecycle (`Proposed` → `Accepted` → `Superseded`/`Deprecated`).
- At least the first six backfill ADRs (two fully written here, four scaffolded with enough context to expand).
- Workflow wiring: a `.github/pull_request_template.md` that prompts for an ADR, and a link from the
  top-level `README.md`.

The decisions to backfill are real and observable in the tree today:

| # | Decision | Evidence in repo |
|---|----------|------------------|
| 1 | Brand multitenancy via `X-App-Brand` header + `ThreadLocal` | `platform/tenancy/BrandContext.java`, `BrandContextFilter.java` |
| 2 | Server-side session auth (Redis) instead of JWT | `platform/security/SecurityConfig.java`, `auth/service/AuthService.java`, `build.gradle` (`spring-session-data-redis`), `application.yaml` (`session.store-type: redis`) |
| 3 | `JdbcClient` instead of JPA/Hibernate | `auth/repository/UserRepository.java`, `scenarios/repository/InvestmentScenarioRepository.java` |
| 4 | Application-managed timestamps via injectable `Clock` | `NGNFinancialCalcApplication.java` (`Clock` bean), `UserRepository.java` (`LocalDateTime.now(clock).truncatedTo(MICROS)`) |
| 5 | Anti-enumeration registration + tenant-scoped writes | `AuthService.register(...)`, `UserRepository.updateUser(...)` (`WHERE brand = :brand AND id = :id`) |
| 6 | Liquibase migration discipline (append-only changesets) | `db/changelog/db.changelog-master.yaml` + `changesets/00{1..4}-*.yaml` |

---

## 2. Why this signals seniority

- **Decisions outlive people.** The multitenancy model and the session-vs-JWT call are the kind of choices a
  new engineer will silently violate (e.g. "let's just add JWT for the mobile client") unless the *reasoning*
  is written down. ADRs capture the forces, not just the outcome.
- **It shows the author knows what is architecturally significant.** Backfilling exactly these six — and not,
  say, "we use Lombok" — demonstrates judgment about which decisions are expensive to reverse.
- **It is auditable.** For a financial product with per-brand tenant isolation and anti-enumeration auth, being
  able to point a reviewer/auditor at "here is why registration returns a generic error and always hashes" is
  concrete evidence of deliberate, defensible engineering.
- **It is cheap and durable.** Markdown-in-repo ADRs version with the code, review through the same PR flow, and
  need no external tool. Choosing the boring, low-maintenance option over a wiki is itself a senior instinct.

---

## 3. Current state

- **No ADRs exist.** There is a `doc/` directory at repo root and per-module `README.md` files, but no
  `docs/adr/`, no `architecture/decisions/`, and no numbered decision files anywhere.
- The rationale for the six decisions above currently survives only as:
  - inline comments (good ones — e.g. `// Always hash before touching the DB so response timing does not reveal whether the email exists.` in `AuthService`),
  - the `WHERE brand = :brand AND id = :id` pattern repeated across repositories,
  - the `Clock` bean + `truncatedTo(ChronoUnit.MICROS)` pattern,
  - commit history.
- **No PR template** exists under `.github/` (only `.github/workflows/backend.yml` and `frontend.yml`).
- This is *green-field for documentation* but *brown-field for decisions* — the ideal condition for backfilling.

---

## 4. Format choice: MADR (Markdown ADR)

**Decision for this plan: use MADR (Markdown Architectural Decision Records), lightly trimmed.**

Justification:

- **Markdown-native, in-repo.** Reviews through the existing GitHub PR flow; no wiki drift; diffs are readable.
- **MADR > raw Nygard for this team.** Nygard's original 2011 format (Context / Decision / Consequences) is the
  intellectual foundation and is perfectly fine, but MADR adds first-class **Status**, **Considered Options**, and
  **Pros/Cons of options** fields. Those matter here because several of our decisions had real alternatives
  (JWT vs session; JPA vs JdbcClient) that a reader will want to see weighed, not just the winner.
- **Tooling-friendly.** `adr-tools` and `log4brains` both understand this file/number convention, so we keep the
  door open without adopting a tool now.
- **We trim MADR's optional fields.** We keep the full template available but mark advanced sections
  (`Confirmation`, `More Information`) as optional so a routine ADR stays a 10-minute job.

We deliberately **reject**: Confluence/Notion (drifts from code, not reviewed with the change), and a single
monolithic `DECISIONS.md` (does not diff cleanly, no per-decision status).

---

## 5. Step-by-step execution

### Step 0 — Preconditions
- Work on a branch off `develop` (repo default branch), e.g. `docs/adr-bootstrap`.
- No build/test changes are required by this plan; it is documentation + a PR template only.

### Step 1 — Create the directory and index

Create `docs/adr/` (repo root, sibling of the existing `doc/`; use `docs/adr` because it is the convention
`adr-tools`/`log4brains` expect).

Create **`docs/adr/README.md`**:

```markdown
# Architecture Decision Records

This directory records the architecturally significant decisions made in this project,
using the [MADR](https://adr.github.io/madr/) format.

## What is an ADR?
A short document capturing one decision: its context, the option chosen, and the
consequences. See Michael Nygard, *Documenting Architecture Decisions* (2011).

## When to write one
Write an ADR when a change is **expensive to reverse** or **constrains future work**:
choice of a framework/library, a security or tenancy model, a data-access strategy, an
API contract, a persistence or migration convention, a cross-cutting pattern every
module must follow. If in doubt, write one — they are cheap.

## How to add an ADR
1. Copy `0000-adr-template.md` to `NNNN-short-title.md` (next free 4-digit number, kebab-case title).
2. Fill it in. Start at status **Proposed**.
3. Open a PR. Discussion happens on the PR. On merge, set status to **Accepted**.
4. Never edit an Accepted ADR's decision. To change a decision, write a **new** ADR and set the old one
   to **Superseded by ADR-NNNN** (add a link both ways).
5. Add a row to the index below.

## Status lifecycle
`Proposed` → `Accepted` → (`Deprecated` | `Superseded by ADR-NNNN`)
`Rejected` is also valid for options considered and turned down but worth recording.

## Index
| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-brand-multitenancy-header-threadlocal.md) | Brand multitenancy via header + ThreadLocal | Accepted |
| [0002](0002-server-side-session-auth-over-jwt.md) | Server-side session auth (Redis) over JWT | Accepted |
| [0003](0003-jdbcclient-over-jpa-hibernate.md) | JdbcClient over JPA/Hibernate | Accepted |
| [0004](0004-application-managed-timestamps-injectable-clock.md) | Application-managed timestamps via injectable Clock | Accepted |
| [0005](0005-anti-enumeration-registration-tenant-scoped-writes.md) | Anti-enumeration registration + tenant-scoped writes | Accepted |
| [0006](0006-liquibase-append-only-migration-discipline.md) | Liquibase append-only migration discipline | Accepted |
```

### Step 2 — Create the template

Create **`docs/adr/0000-adr-template.md`** with the full MADR template below (copy verbatim):

```markdown
---
# These are optional metadata elements. Feel free to remove any of them.
status: "Proposed"           # Proposed | Accepted | Deprecated | Superseded by ADR-NNNN | Rejected
date: YYYY-MM-DD
deciders: [names or roles]
consulted: []
informed: []
---

# NNNN. <short, decision-focused title>

## Context and Problem Statement

<Describe the context and the problem in 2–4 sentences. What forces are at play?
Frame it as a question the ADR answers, e.g. "How should we isolate tenant data?">

## Decision Drivers

- <driver, e.g. tenant data must never leak across brands>
- <driver, e.g. team is small; minimise operational surface>

## Considered Options

- <option 1>
- <option 2>
- <option 3>

## Decision Outcome

Chosen option: "<option>", because <justification — which drivers it best satisfies>.

### Consequences

- Good, because <positive consequence>.
- Bad, because <cost / trade-off / thing we now must live with>.
- Neutral, because <follow-on constraint>.

## Pros and Cons of the Options

### <option 1>
- Good, because <...>
- Bad, because <...>

### <option 2>
- Good, because <...>
- Bad, because <...>

## Confirmation
<Optional. How do we confirm the decision is implemented as described?
e.g. a test, a code-review checklist item, an ArchUnit rule.>

## More Information
<Optional. Links to the code, related ADRs, external references. Add a
"Superseded by ADR-NNNN" note here if this ADR is later replaced.>
```

### Step 3 — Write the six backfill ADRs

Create the six files listed in the index. **ADR-0002 and ADR-0004 are fully written in Section 6 below —
copy them verbatim.** For the other four, create the file, set `status: "Accepted"`, and use the
one-paragraph summaries in Section 7 to fill Context / Decision Outcome / Consequences; expand the
Considered Options and Pros/Cons from the template. Set `date` to the merge date; `deciders` to the team.

### Step 4 — Add the PR template

Create **`.github/pull_request_template.md`**:

```markdown
## What & why
<Short description of the change and the motivation.>

## Architecture decisions
- [ ] This PR introduces or changes an architecturally significant decision (framework,
      security/tenancy model, data-access strategy, API contract, migration convention,
      cross-cutting pattern).
  - If checked: an ADR is added/updated under `docs/adr/` in this PR → ADR-____
  - If not checked: no ADR needed.
- [ ] This PR is consistent with existing ADRs (see `docs/adr/README.md`); if it
      contradicts one, a superseding ADR is included.

## Checklist
- [ ] Tests added/updated and passing (`./gradlew test` for backend)
- [ ] DB changes are a **new** Liquibase changeset (never edited an applied one) — see ADR-0006
- [ ] No tenant-scope regressions: reads/writes remain scoped by `brand` — see ADR-0001 / ADR-0005
```

### Step 5 — Wire into the top-level README

Edit `/README.md` (repo root). Add a short section near the top of the project structure/docs area:

```markdown
## Architecture Decision Records
Significant technical decisions are recorded under [`docs/adr/`](docs/adr/README.md)
(MADR format). Read these before changing the auth model, tenancy model, or data-access layer.
```

### Step 6 — (Optional) adopt tooling later

Do **not** add a tool in this PR. Note in `docs/adr/README.md` (already covered) that the file convention is
`adr-tools`-compatible. If the team later wants automation:
- `adr-tools` (`brew install adr-tools`; `adr new "<title>"` auto-numbers and creates the file), or
- `log4brains` (`npx log4brains init`) to publish a searchable static ADR site from the same Markdown.
Both read the exact `docs/adr/NNNN-*.md` convention this plan establishes, so adoption is zero-migration.

### Step 7 — Commit & PR
- Commit the new `docs/adr/**`, `.github/pull_request_template.md`, and `README.md` edit together.
- Open a PR into `develop`. The new PR template will render — tick "no ADR needed" (this PR *is* the ADR
  bootstrap, documented by its own description).

---

## 6. Fully-written example ADRs

### 6.1 — `docs/adr/0002-server-side-session-auth-over-jwt.md`

```markdown
---
status: "Accepted"
date: 2026-07-01
deciders: [backend team]
consulted: []
informed: [frontend team]
---

# 0002. Server-side session authentication (Redis) over JWT

## Context and Problem Statement

The API must authenticate users of a multi-tenant financial calculator and carry that identity
across requests. How should we represent and persist an authenticated session: a stateless
signed token (JWT) held by the client, or a server-side session with an opaque cookie?

## Decision Drivers

- Ability to **revoke** a session immediately (logout, credential compromise) — important for a financial app.
- Small team, low operational surface; we already run Redis.
- Sessions are tenant-aware: identity is a `(brand, userId)` pair, not just a user id.
- Avoid shipping sensitive claims to, and trusting, the browser.

## Considered Options

- **Server-side sessions in Redis** (Spring Session + `SESSION` cookie).
- **Stateless JWT** (access token in header / cookie, optional refresh token).
- **Stateful JWT with a server-side denylist** (hybrid).

## Decision Outcome

Chosen option: **server-side sessions backed by Redis**, because it gives us instant revocation and
keeps all identity on the server, at an operational cost we already pay (Redis is a dependency for
`spring-session-data-redis`).

Implementation as it stands:
- `build.gradle` pulls `org.springframework.session:spring-session-data-redis` and
  `spring-boot-starter-data-redis`.
- `application.yaml` sets `spring.session.store-type: redis` under namespace `spring:session:ngn_calc`.
- `SecurityConfig` uses `SessionCreationPolicy.IF_REQUIRED`, an `HttpSessionSecurityContextRepository`,
  CSRF disabled (SPA + custom brand header), and a logout handler that deletes the `SESSION` cookie and
  invalidates the session.
- `AuthService.login(...)` authenticates via a custom `BrandAuthentication`, then stores the
  `SecurityContext` on the session under `SPRING_SECURITY_CONTEXT_KEY`.

### Consequences

- Good, because logout / forced revocation is a single Redis delete; no token-expiry window to reason about.
- Good, because no sensitive claims leave the server; the cookie is an opaque id.
- Good, because Redis-backed sessions survive app restarts and scale horizontally across instances.
- Bad, because every authenticated request needs a Redis lookup and Redis becomes a hard dependency
  for auth availability.
- Bad, because a purely token-based client (e.g. a future third-party API consumer) is not served by this
  model and would need a separate decision (a new ADR).
- Neutral, because CSRF is disabled; this is acceptable only while auth is a cookie + a required custom
  header (`X-App-Brand`) and CORS is locked down — revisit if that changes.

## Pros and Cons of the Options

### Server-side sessions in Redis
- Good: instant revocation; no client-side secret material; trivial to reason about.
- Bad: stateful; Redis on the hot path for every request.

### Stateless JWT
- Good: no per-request store lookup; naturally horizontal.
- Bad: revocation requires a denylist (defeating statelessness) or short TTL + refresh dance;
  claims live in the browser; key rotation is operationally fiddly.

### Stateful JWT with denylist
- Good: keeps JWT ergonomics for some clients.
- Bad: worst of both — a token store *and* token crypto to maintain.

## Confirmation
`UserFlowIntegrationTest` exercises login → authenticated request → logout. Reviewers reject PRs that
introduce JWT parsing/signing in the auth path without a superseding ADR.

## More Information
- `platform/security/SecurityConfig.java`
- `auth/service/AuthService.java`
- `auth/security/BrandAuthenticationProvider.java`, `BrandAuthentication.java`
- Related: ADR-0001 (tenancy — identity is `(brand, userId)`).
```

### 6.2 — `docs/adr/0004-application-managed-timestamps-injectable-clock.md`

```markdown
---
status: "Accepted"
date: 2026-07-01
deciders: [backend team]
consulted: []
informed: []
---

# 0004. Application-managed timestamps via an injectable Clock

## Context and Problem Statement

Rows in `users` and `investment_scenarios` carry `created_at` / `updated_at`. Where should these
timestamps come from — the database (`DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE`), or the application?
And how do we make time deterministic in tests?

## Decision Drivers

- Timestamps must be **testable** (deterministic) without sleeping or hitting the DB clock.
- The returned entity should reflect exactly what was persisted (no drift between app value and DB value).
- Columns are MySQL `TIMESTAMP(6)` (microsecond precision); Java `LocalDateTime.now()` has nanosecond
  precision, so a naive value round-trips differently than it is stored.
- Consistency: one authority for "now" across all repositories.

## Considered Options

- **Application sets timestamps using an injected `java.time.Clock`.**
- **Database defaults** (`DEFAULT CURRENT_TIMESTAMP`, `ON UPDATE CURRENT_TIMESTAMP`).
- **Application uses `LocalDateTime.now()` directly** (no injectable clock).

## Decision Outcome

Chosen option: **application-managed timestamps via a single injected `Clock` bean**, because it makes time
deterministic in tests, keeps a single source of truth, and lets us truncate to the column's precision so the
in-memory entity matches the stored row exactly.

Implementation as it stands:
- `NGNFinancialCalcApplication` defines `@Bean Clock clock() { return Clock.system(ZoneOffset.UTC); }`.
- Repositories inject `Clock` and compute
  `LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)` (see `UserRepository.insertUser` /
  `updateUser`, and `InvestmentScenarioRepository`).
- The truncated value is passed as a bind parameter *and* returned on the rebuilt entity
  (`user.toBuilder().createdAt(now).updatedAt(now)...`), so no re-read is needed.

### Consequences

- Good, because tests can inject a fixed `Clock` (`Clock.fixed(...)`) and assert exact timestamps.
- Good, because the returned object equals the persisted row (same micros), no post-insert `SELECT`.
- Good, because "now" is UTC and centrally defined; no per-call zone ambiguity.
- Bad, because timestamps depend on app-server clock correctness rather than the DB's; clock skew across
  instances is now our concern (mitigated by NTP).
- Neutral, because every repository must remember to set/`truncatedTo(MICROS)` and scope updates correctly;
  this is a convention enforced by review, not the compiler.

## Pros and Cons of the Options

### Injected Clock (chosen)
- Good: deterministic tests; single UTC authority; entity matches DB exactly.
- Bad: relies on app clock; convention must be applied consistently in each repo.

### Database defaults
- Good: DB is the single clock; nothing to set in code.
- Bad: not testable without the DB; app must re-`SELECT` to learn the stored value; `ON UPDATE` semantics
  vary by engine and are awkward with explicit column lists.

### `LocalDateTime.now()` directly
- Good: simplest to type.
- Bad: not injectable/testable; nanosecond value mismatches the `TIMESTAMP(6)` column.

## Confirmation
Repository tests inject a fixed `Clock` and assert `created_at` / `updated_at`. A new repository that
writes timestamps without the injected `Clock` should be flagged in review.

## More Information
- `NGNFinancialCalcApplication.java` (Clock bean)
- `auth/repository/UserRepository.java`, `scenarios/repository/InvestmentScenarioRepository.java`
- Related: ADR-0003 (JdbcClient — we control SQL, so we own the timestamp columns explicitly);
  ADR-0006 (the `TIMESTAMP(6)` precision is fixed by Liquibase changesets 002/004).
```

---

## 7. Backlog — prioritized, with expansion summaries

Priority order reflects "how expensive to get wrong / how likely a newcomer violates it".

### ADR-0001 — Brand multitenancy via header + ThreadLocal  *(priority 1)*
**File:** `docs/adr/0001-brand-multitenancy-header-threadlocal.md`
**Context + Decision + Consequences (expand into template):** The product serves multiple brands from one
deployment and tenant data must never cross brands. We resolve the active tenant from a **mandatory
`X-App-Brand` request header**, normalise it (`trim().toUpperCase()`), and stash it in a `ThreadLocal`
(`BrandContext`) populated by `BrandContextFilter` (an `OncePerRequestFilter` registered *before*
`UsernamePasswordAuthenticationFilter`), cleared in a `finally` to avoid leakage across pooled threads.
Service/repository code reads `BrandContext.get()`, which throws if the header was absent. *Consequences:*
tenant scoping is ambient and terse (no `brand` param threaded through every method), but it depends on the
filter running and the `ThreadLocal` being cleared — and **must be revisited if we adopt reactive/virtual-thread
handoffs or async execution** where `ThreadLocal` does not propagate. Considered alternatives: subdomain/path
tenant resolution; a `brand` method parameter everywhere; schema-per-tenant.

### ADR-0002 — Server-side session auth over JWT  *(priority 2)* — **fully written in §6.1**

### ADR-0003 — JdbcClient over JPA/Hibernate  *(priority 3)*
**File:** `docs/adr/0003-jdbcclient-over-jpa-hibernate.md`
**Context + Decision + Consequences:** For data access we use Spring's `JdbcClient` with hand-written SQL and
explicit `RowMapper`s (see `UserRepository`), plus `GeneratedKeyHolder` for generated ids, rather than
JPA/Hibernate. Drivers: full control over the exact SQL (needed for tenant-scoped `WHERE brand AND id`
writes and precise `TIMESTAMP(6)` handling), no lazy-loading/N+1/dirty-checking surprises, minimal mapping
magic, and predictable performance for a small, well-understood schema. DTOs are Java records; entities are
plain records/builders, not `@Entity` graphs. *Consequences:* more boilerplate SQL and manual mapping, and
no free change-tracking or cascade — but transparent, debuggable queries and no ORM tuning. Revisit if the
domain grows a large, deeply-related graph. Considered alternatives: Spring Data JPA/Hibernate; jOOQ;
MyBatis; raw `JdbcTemplate`.

### ADR-0004 — Application-managed timestamps via injectable Clock  *(priority 4)* — **fully written in §6.2**

### ADR-0005 — Anti-enumeration registration + tenant-scoped writes  *(priority 5)*
**File:** `docs/adr/0005-anti-enumeration-registration-tenant-scoped-writes.md`
**Context + Decision + Consequences:** Registration must not let an attacker discover which emails are
registered. Decision: `AuthService.register` **always** BCrypt-hashes the password before touching the DB
(timing-equalized whether or not the email exists), relies on the `UNIQUE(brand, email)` constraint as the
single source of truth (no check-then-insert race), and on `DuplicateKeyException` returns a **generic**
`REGISTRATION_FAILED` with no field-level detail — never "email already in use". Complementarily, every write
is tenant-scoped and brand-immutable: `updateUser` uses `WHERE brand = :brand AND id = :id` and never puts
`brand` in the `SET` clause; a 0-row update raises `USER_NOT_FOUND` rather than silently succeeding.
*Consequences:* stronger privacy and no cross-tenant update/leak, at the cost of less specific client-side
error messages (frontend cannot say "that email is taken") and a small, deliberate constant-time cost on the
happy path. Considered alternatives: check-existence-then-insert (rejected: race + enumeration); short-circuit
without hashing (rejected: timing oracle); returning specific validation errors (rejected: enumeration).

### ADR-0006 — Liquibase append-only migration discipline  *(priority 6)*
**File:** `docs/adr/0006-liquibase-append-only-migration-discipline.md`
**Context + Decision + Consequences:** Schema evolves via Liquibase (`spring-boot-starter-liquibase`), with a
master changelog (`db/changelog/db.changelog-master.yaml`) that `include`s one file per change under
`changesets/` (currently `001-create-users-and-scenarios`, `002-users-created-at-precision`,
`003-users-updated-at`, `004-scenarios-created-at-precision`). Decision/discipline: **every schema change is a
new, numbered changeset file; already-applied changesets are never edited** (editing one changes its checksum
and breaks validation on environments that already ran it). Corrections are made forward with a new changeset.
This is why timestamp-precision fixes shipped as 002 and 004 rather than edits to 001. *Consequences:* a clean,
replayable, auditable migration history and safe deploys across environments, at the cost of more files and the
occasional "fix-forward" changeset instead of tidying an earlier one. Considered alternatives: Flyway (viable;
Liquibase chosen for its YAML changelog and Spring Boot starter); Hibernate `ddl-auto` (rejected — unsafe,
non-auditable, and moot since we do not use JPA — see ADR-0003).

---

## 8. Best-practice notes

- **One decision per ADR.** If you find yourself writing "and also", split it.
- **ADRs are immutable once Accepted** — except their `status`. Don't rewrite history; supersede it. A superseded
  ADR stays in the repo (it is the historical record) with a link forward, and the new one links back.
- **Number monotonically, zero-padded to 4 digits.** Never reuse a number, even for a rejected ADR.
- **Title = the decision, not the topic.** "Server-side session auth over JWT", not "Authentication".
- **Write it when the decision is made, in the same PR** as the code that implements it — that is the whole
  point of keeping ADRs in the repo. (These six are backfills; going forward, ADR-with-the-code is the rule the
  PR template enforces.)
- **Keep them short.** Context, drivers, options, outcome, consequences. If it is longer than ~1.5 pages, you are
  writing a design doc, not an ADR — link the design doc from `More Information` instead.
- **Prefer honesty about trade-offs.** The `Bad, because…` lines are the most valuable part; an ADR with no
  downsides listed is not credible.
- **Do not retro-fit a fake decision.** For backfills, describe the reasoning as it actually was; if an option
  was never seriously considered, say so briefly rather than inventing a comparison.

## 9. Effort estimate

| Task | Effort |
|------|--------|
| `docs/adr/` + `README.md` index + `0000-adr-template.md` | 30 min |
| ADR-0002 and ADR-0004 (copy from this plan, adjust dates/deciders) | 15 min |
| ADR-0001, 0003, 0005, 0006 (expand from §7 summaries into template) | 2–3 h total (~30–45 min each) |
| `.github/pull_request_template.md` | 15 min |
| Top-level `README.md` wiring | 10 min |
| PR + review | 30 min |
| **Total** | **~half a day** |

Optional follow-up (separate PR): adopt `adr-tools` or `log4brains` — ~1–2 h.

## 10. References

- Michael Nygard, *Documenting Architecture Decisions* (2011) — the original ADR concept
  (Context / Decision / Consequences): https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions
- MADR — Markdown Any/Architectural Decision Records: https://adr.github.io/madr/
- ADR org / overview & tooling: https://adr.github.io/
- `adr-tools` (Nat Pryce): https://github.com/npryce/adr-tools
- `log4brains` (publishable ADR site): https://github.com/thomvaill/log4brains
```
