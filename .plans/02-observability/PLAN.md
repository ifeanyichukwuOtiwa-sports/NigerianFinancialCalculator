# Observability Implementation Plan — Metrics, Tracing, Structured Logging

> Scope: `backEnd` (Spring Boot 4.0.5, Java 25, Gradle) + `frontEnd` (Angular).
> Deployment: Railway, split domains (frontend + backend on separate public hosts).
> This plan is concrete and repo-grounded. Execute top-to-bottom.

---

## 1. Objective

Give this service the three pillars of observability, wired to work together:

1. **Metrics** — expose Micrometer metrics in Prometheus format (`/actuator/prometheus`), including RED metrics (Rate, Errors, Duration) for HTTP endpoints, JVM, Redis, HikariCP, and bucket4j rate limiting.
2. **Distributed tracing** — emit OpenTelemetry (OTLP) spans via Micrometer Tracing, propagate W3C `traceparent` from the Angular frontend through the backend, so a single user action produces one connected trace.
3. **Structured logging** — emit JSON logs (via Spring Boot 4's built-in `logging.structured.format`) that carry `traceId`, `spanId`, a per-request `correlationId`, and the tenant `brand` on **every** line — including the output of the existing `CustomLogger` / `GlobalExceptionHandler`, with zero changes to those two classes.

Non-goals: log aggregation infra provisioning (Loki/Tempo cluster setup is ops), alerting rules, SLO burn-rate policy. We produce the signals and a starter dashboard; ops consumes them.

---

## 2. Why this signals seniority

- **It's the seam between "it works on my machine" and "we can operate this in production."** Anyone can add a logger; a senior engineer makes a bug in prod diagnosable in minutes by pivoting from a Grafana latency spike → the exact trace → the correlated JSON log lines, all keyed by one ID.
- **Correlation across the stack.** Propagating `traceparent` from Angular means a customer-reported "it was slow at 14:32" becomes one clickable trace instead of a log-grep archaeology dig.
- **Multi-tenant awareness.** Putting `brand` in the MDC/metrics context (carefully — see cardinality pitfall) lets us answer "is brand X degraded?" without new code per incident.
- **Security judgement.** Metrics/trace endpoints leak internal topology and can be a DoS surface. Choosing to *not* expose `/actuator/prometheus` publicly on a Railway split-domain deployment, and securing it, is the differentiator between "added Prometheus" and "added Prometheus responsibly."
- **Runtime-model awareness.** Knowing that Java 25 virtual threads + `ThreadLocal` MDC has a context-propagation caveat (and handling it) is exactly the kind of detail that separates senior from mid.

---

## 3. Current state in this repo (cited)

| Concern | Current state | File |
|---|---|---|
| Actuator | Present as dependency; only `health,info` exposed | `backEnd/build.gradle:31`, `backEnd/src/main/resources/application.yaml:39-53` |
| Micrometer registry | **None** (no Prometheus registry, no tracing bridge, no OTLP exporter) | `backEnd/build.gradle:23-51` |
| Logging | Default Spring Boot logback, **plain text**, no `logback.xml`, no structured config | (no `logback*.xml` in `backEnd/src/main/resources`) |
| Error logging | `CustomLogger` (SLF4J `@Slf4j`) called by `GlobalExceptionHandler` | `backEnd/src/main/java/iwo/wintech/ngnfincalc/shared/logging/CustomLogger.java`, `.../shared/error/GlobalExceptionHandler.java` |
| Tenancy context | `BrandContext` (ThreadLocal, `X-App-Brand` header), set/cleared in `BrandContextFilter` (an `OncePerRequestFilter`) | `.../platform/tenancy/BrandContext.java`, `.../platform/tenancy/BrandContextFilter.java` |
| Filter order | In `SecurityConfig`: `rateLimitingFilter` → `brandContextFilter` → `UsernamePasswordAuthenticationFilter` | `.../platform/security/SecurityConfig.java:36-37` |
| Actuator security | `permitAll()` for `/actuator/health`, `/actuator/health/**`, `/actuator/info`; **everything else `authenticated()`** | `.../platform/security/SecurityConfig.java:42-47` |
| Frontend HTTP | `brandInterceptor` sets `x-app-brand`; `authInterceptor`; registered via `provideHttpClient(withInterceptors([...]))` | `frontEnd/src/app/core/interceptors/brand.interceptor.ts`, `frontEnd/src/app/app.component.config.ts:12` |
| CORS | Allowed headers configurable; currently `authorization,content-type,x-auth-token,x-app-brand` | `application.yaml:59` |
| Virtual threads | **Not explicitly enabled** (`spring.threads.virtual.enabled` absent). Java 25 toolchain. | `application.yaml`, `build.gradle:15` |

---

## 4. Gaps

1. No metrics registry → nothing to scrape; `/actuator/prometheus` doesn't exist.
2. No tracing → no `traceId`/`spanId` generated, so nothing to put in logs or correlate.
3. Logs are plain text and carry no trace/correlation/tenant context — the `CustomLogger` messages are unlinkable to a request or a trace.
4. No request correlation ID; a client cannot supply or receive one.
5. Frontend does not send `traceparent`, so backend traces start fresh and can never join the frontend's view of a request.
6. Because `anyRequest().authenticated()` is the catch-all, if we blindly expose `prometheus`/`metrics` they'd be *authenticated* (good default) — but we must consciously decide and document the exposure model rather than accidentally opening them.
7. Java 25 + (future) virtual threads: the `ThreadLocal` MDC and `BrandContext` won't automatically propagate to child virtual threads spawned inside a request — a latent correctness gap for async work.

---

## 5. Step-by-step

### Step 0 — Pin versions / BOM sanity (read before touching build.gradle)

- Spring Boot `4.0.5` (`build.gradle:3`) manages **Micrometer 1.15+** and **Micrometer Tracing 1.5+** via its dependency management plugin (`io.spring.dependency-management`, `build.gradle:4`). Do **not** hard-pin versions for Spring-managed artifacts; let the BOM drive them.
- The one artifact whose version you may need to align is the **OpenTelemetry OTLP exporter** (`io.opentelemetry:opentelemetry-exporter-otlp`). Spring Boot's BOM imports the OpenTelemetry BOM, so this is usually managed too. Only add an explicit `opentelemetry-bom` import if a version resolution warning appears at build time.
- Micrometer Tracing offers two bridges: **OTel bridge** (`micrometer-tracing-bridge-otel`) and Brave/Zipkin bridge. We choose the **OTel bridge** to standardize on OpenTelemetry semantics and OTLP export (vendor-neutral: Tempo, Honeycomb, etc.).

### Step 1 — Add dependencies (`backEnd/build.gradle`)

Add inside the `dependencies { }` block (after line 31, near the actuator entry):

```gradle
    // --- Observability: metrics ---
    implementation 'io.micrometer:micrometer-registry-prometheus'      // /actuator/prometheus, version managed by Spring Boot BOM

    // --- Observability: tracing (Micrometer Tracing -> OpenTelemetry -> OTLP) ---
    implementation 'io.micrometer:micrometer-tracing-bridge-otel'      // Micrometer Tracing SPI backed by OpenTelemetry
    implementation 'io.opentelemetry:opentelemetry-exporter-otlp'      // exports spans over OTLP (gRPC/HTTP) to Tempo/Honeycomb/collector

    // --- Optional: auto-instrument RestClient/RestTemplate outbound calls for trace propagation ---
    // (spring-boot-starter-restclient is already present, build.gradle:32; Micrometer observation
    //  auto-configures RestClient instrumentation when a tracer is on the classpath — no extra dep needed.)
```

Notes:
- `micrometer-registry-prometheus` transitively pulls `micrometer-core`; the actuator starter already present will auto-configure the `prometheus` endpoint once the registry is on the classpath.
- No `spring-boot-starter-aop` is strictly required, but if you later use `@Observed`/`@Timed` annotation-based spans, add `implementation 'org.springframework.boot:spring-boot-starter-aop'`. Not needed for this plan.
- Do **not** add `spring-boot-starter-actuator` again — it's already at `build.gradle:31`.

After editing, run `./gradlew :backEnd:dependencies --configuration runtimeClasspath | grep -i 'micrometer\|opentelemetry'` to confirm versions resolve from the BOM (no `FAILED`).

### Step 2 — Expose + secure actuator endpoints (`application.yaml`)

Replace the `management:` block (`application.yaml:39-53`) with:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
  # ----- Metrics / tracing config -----
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      # enable histogram buckets so Grafana can compute latency percentiles (p50/p95/p99) server-side
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 50ms,100ms,200ms,400ms,1s,2s
  observations:
    key-values:
      # (optional) global low-cardinality tags applied to all observations
      deployment.env: ${DEPLOY_ENV:local}
  tracing:
    sampling:
      # sample 100% locally; override to 0.1 (10%) in prod via env to control cost/volume
      probability: ${TRACING_SAMPLE_PROBABILITY:1.0}
    propagation:
      # accept + emit W3C traceparent (matches the frontend interceptor in Step 7)
      type: W3C
  otlp:
    tracing:
      # OTLP endpoint of your collector / Tempo / Honeycomb. Empty by default so local dev
      # does not fail if no collector is running (see Step 6 for targets).
      endpoint: ${OTLP_TRACES_ENDPOINT:}
      # headers for SaaS backends (e.g. Honeycomb: x-honeycomb-team=<key>)
      # headers:
      #   x-honeycomb-team: ${HONEYCOMB_API_KEY:}
```

**Exposure decision (do NOT expose publicly):** On Railway with split domains, the backend host is public. `include: prometheus,metrics` only *registers* the endpoints on the web layer — access is still gated by Spring Security. Because `SecurityConfig` ends with `anyRequest().authenticated()` (`SecurityConfig.java:46`), `/actuator/prometheus` and `/actuator/metrics` are **authenticated by default** — they are NOT public. We keep it that way and additionally lock them down explicitly in Step 3. Two acceptable production postures:

- **(A) Separate management port (preferred for scraping).** Set `management.server.port: ${MANAGEMENT_PORT:9090}` and `management.server.address: 127.0.0.1` (or a private-network bind). Prometheus/collector scrapes the private port; the public `8080` never serves `/actuator/prometheus`. On Railway, expose only `8080` publicly and scrape `9090` over the private network. **Trade-off:** a separate port means the main `SecurityFilterChain` does not apply to it; secure it with a dedicated management filter chain or rely on network isolation (private bind). Document whichever you pick.
- **(B) Same port, secured by role.** Keep one port; require an authenticated principal (or a dedicated `SCRAPE` role / basic-auth service account) for `/actuator/prometheus` and `/actuator/metrics` (Step 3). Simpler on Railway if you scrape via an authenticated agent (e.g. Grafana Alloy with a bearer token / basic auth).

Given Railway's split-domain, single-service simplicity, **recommend (B)** with a dedicated scrape credential, unless you run a private collector — then (A). This plan implements (B).

### Step 3 — Protect the new endpoints in Security (`SecurityConfig.java`)

Do **not** add `/actuator/prometheus` or `/actuator/metrics` to the `permitAll()` list at `SecurityConfig.java:44`. Keep the existing health/info permits. Add an explicit rule so intent is documented and the fallback `authenticated()` isn't the only thing protecting them.

Change the `authorizeHttpRequests` block (`SecurityConfig.java:42-47`) to:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
    // Metrics/trace endpoints are NEVER public: require an authenticated scrape identity.
    .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
        .hasRole("METRICS")           // or .authenticated() if any logged-in user may read
    .requestMatchers("/error").permitAll()
    .anyRequest().authenticated()
)
```

Provision a scrape identity. Options, cheapest first:
- **HTTP Basic for the scraper only:** add `http.httpBasic(Customizer.withDefaults())` and register an in-memory `UserDetails` service user with role `METRICS`, credentials from env (`METRICS_USER`/`METRICS_PASSWORD`). Prometheus/Alloy scrape config supplies `basic_auth`.
- Or a bearer/API-key filter if you prefer.

Add (only if using Basic) a bean:

```java
@Bean
public UserDetailsService metricsUserDetailsService(PasswordEncoder encoder,
        @Value("${metrics.scrape.username:}") String user,
        @Value("${metrics.scrape.password:}") String pass) {
    if (user.isBlank()) return new InMemoryUserDetailsManager(); // no scrape user configured
    return new InMemoryUserDetailsManager(
        User.withUsername(user).password(encoder.encode(pass)).roles("METRICS").build());
}
```

> Note: this service is Redis-session, form/credential based (`HttpSessionSecurityContextRepository`, `SecurityConfig.java:39-41`). Adding `httpBasic` for the metrics matchers does not interfere with session auth for `/api/**` — Basic is only attempted when the client sends an `Authorization: Basic` header. Confirm no conflict with the existing `authInterceptor`/`x-auth-token` scheme in tests.

### Step 4 — Correlation-ID + MDC filter (new file)

Create `backEnd/src/main/java/iwo/wintech/ngnfincalc/platform/observability/CorrelationIdFilter.java`.

Responsibilities:
- Read an inbound `X-Correlation-Id` (or generate a UUID if absent).
- Put `correlationId` in MDC; echo it back on the response header so clients/logs line up.
- The `traceId`/`spanId` are injected into MDC automatically by Micrometer Tracing's logging correlation (no manual work) — but only once a span is active. This filter runs inside the trace, so those keys are present for downstream log lines.
- Clear MDC in `finally` (mandatory — pooled platform threads must not leak MDC).

```java
package iwo.wintech.ngnfincalc.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5) // run early so all downstream logs carry the id
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_CORRELATION_ID, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
```

### Step 5 — Put `brand` into the MDC via the existing `BrandContextFilter`

Edit `backEnd/src/main/java/iwo/wintech/ngnfincalc/platform/tenancy/BrandContextFilter.java` so the tenant appears on every log line for the request. Minimal, surgical change — keep the ThreadLocal behaviour, add MDC alongside it:

```java
package iwo.wintech.ngnfincalc.platform.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class BrandContextFilter extends OncePerRequestFilter {

    private static final String MDC_BRAND = "brand";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String brand = request.getHeader("X-App-Brand");
            if (brand != null && !brand.isBlank()) {
                String normalized = brand.trim().toUpperCase();
                BrandContext.set(normalized);
                MDC.put(MDC_BRAND, normalized);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_BRAND);
            BrandContext.clear();
        }
    }
}
```

> Ordering: `BrandContextFilter` currently runs after `rateLimitingFilter` and before auth (`SecurityConfig.java:36-37`). That's fine for logging within the request. `CorrelationIdFilter` (Step 4, HIGHEST_PRECEDENCE+5) runs before the security chain, so early logs get a correlation id even before brand is resolved.

### Step 6 — Structured JSON logging (Spring Boot 4 built-in) + MDC keys

Use Spring Boot 4's native structured logging — **no custom appender, no logback.xml, no Logstash encoder dependency.** Add to `application.yaml` (top-level `logging:` key):

```yaml
logging:
  structured:
    format:
      # JSON to stdout. Railway captures stdout -> your log backend.
      # Use "ecs" (Elastic Common Schema) or "logstash". ECS names trace fields
      # trace.id / span.id which most backends understand; pick to match your stack.
      console: ${LOG_FORMAT:ecs}
  # Ensure MDC keys we care about are rendered. Spring Boot's structured formats
  # include MDC automatically; these patterns only affect the *non-structured* fallback
  # (e.g. local dev when LOG_FORMAT is unset/plain).
  pattern:
    correlation: "[%X{traceId:-},%X{spanId:-},%X{correlationId:-},%X{brand:-}]"
```

Behaviour:
- With `logging.structured.format.console=ecs`, every log event is emitted as one JSON object to stdout, and **all MDC entries are included** — so `correlationId`, `brand`, plus `traceId`/`spanId` (added by Micrometer Tracing's `Slf4JEventListener`/logging correlation) appear as fields automatically.
- **`CustomLogger` and `GlobalExceptionHandler` need NO changes.** They log via SLF4J (`@Slf4j`, `CustomLogger.java:12`); the MDC context is thread-bound, so their messages inherit `traceId`/`spanId`/`correlationId`/`brand` for free. This is the payoff of doing correlation at the filter/MDC layer instead of in each logger.
- For **local human-readable** dev, leave `LOG_FORMAT` unset and set `logging.structured.format.console` to empty in a `local` profile (or just don't set the env var and override the default to blank). The `logging.pattern.correlation` above makes plain-text local logs still show the IDs.

Local dev override — add `backEnd/src/main/resources/application-local.yaml`:
```yaml
logging:
  structured:
    format:
      console:   # blank -> falls back to human-readable pattern with correlation IDs
```

### Step 7 — Frontend: propagate W3C `traceparent` (Angular)

Two viable approaches:

**(A) Lightweight header interceptor (recommended if you don't already ship OTel-JS).** Generate a `traceparent` per request so the backend continues the trace and the frontend logs the same id. Create `frontEnd/src/app/core/interceptors/trace.interceptor.ts`:

```ts
import { HttpInterceptorFn } from '@angular/common/http';

// Minimal W3C traceparent: version-traceid-spanid-flags
function hex(bytes: number): string {
  const arr = crypto.getRandomValues(new Uint8Array(bytes));
  return Array.from(arr, b => b.toString(16).padStart(2, '0')).join('');
}

export const traceInterceptor: HttpInterceptorFn = (req, next) => {
  const traceId = hex(16); // 32 hex chars
  const spanId = hex(8);   // 16 hex chars
  const traceparent = `00-${traceId}-${spanId}-01`; // 01 = sampled
  const traced = req.clone({ setHeaders: { traceparent } });
  return next(traced);
};
```

Register it in `frontEnd/src/app/app.component.config.ts` (line 12) — order it **first** so the id exists before other interceptors:
```ts
provideHttpClient(withInterceptors([traceInterceptor, brandInterceptor, authInterceptor])),
```

**(B) Full OpenTelemetry-JS** (`@opentelemetry/sdk-trace-web` + `@opentelemetry/instrumentation-fetch/xhr` + `W3CTraceContextPropagator`). Heavier, but gives real frontend spans exported to the same OTLP backend. Use this only if you want frontend timing in traces; otherwise (A) is enough to link logs and continue traces on the backend.

**CORS requirement (critical):** the browser will only send `traceparent` cross-origin if it's an allowed request header. Add `traceparent` (and `x-correlation-id` if the frontend sends one) to the allowed headers. Edit `application.yaml:59`:
```yaml
    allowed-headers: ${CORS_ALLOWED_HEADERS:authorization,content-type,x-auth-token,x-app-brand,traceparent,x-correlation-id}
    exposed-headers: ${CORS_EXPOSED_HEADERS:x-auth-token,x-correlation-id}
```
Set the same values in the Railway `CORS_ALLOWED_HEADERS` / `CORS_EXPOSED_HEADERS` env vars (they're env-driven). `exposed-headers` lets the frontend read back the `X-Correlation-Id` the backend echoes.

Backend `management.tracing.propagation.type: W3C` (Step 2) makes Micrometer Tracing read the incoming `traceparent` and continue that trace.

### Step 8 — OTLP export target (pick one, all set via env)

`OTLP_TRACES_ENDPOINT` (Step 2) drives where spans go:

| Target | Endpoint value | Notes |
|---|---|---|
| **Grafana Tempo** (self/Grafana Cloud) | `https://<tempo-host>/otlp/v1/traces` (or gRPC `:4317`) | Grafana Cloud needs basic-auth headers; add under `management.otlp.tracing.headers`. Pairs naturally with the Grafana dashboard (Step 9). |
| **Honeycomb** | `https://api.honeycomb.io` | Add header `x-honeycomb-team: ${HONEYCOMB_API_KEY}` and `x-honeycomb-dataset`. Cheapest to stand up, generous free tier. |
| **OTel Collector** (Railway service) | `http://<collector.private>:4318/v1/traces` | Run collector as a second Railway service on the private network; fan out to any backend. Most flexible; recommended if you also scrape metrics privately (Step 2 option A). |
| **Local dev** | leave empty | No collector needed; app still runs. Or run Jaeger all-in-one (`http://localhost:4318`) via Docker to view traces locally. |

Railway-friendly default: **Honeycomb** for lowest ops, or an **OTel Collector service** if you want vendor neutrality on the private network. Set `TRACING_SAMPLE_PROBABILITY=0.1` in prod.

### Step 9 — RED metrics + starter Grafana dashboard

Micrometer + the Prometheus registry give RED for free from the `http.server.requests` timer:
- **Rate:** `sum(rate(http_server_requests_seconds_count[1m])) by (uri, method)`
- **Errors:** `sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) by (uri)` and 4xx separately
- **Duration:** `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))` — requires the histogram buckets enabled in Step 2 (`percentiles-histogram.http.server.requests: true`).

Also available automatically: JVM memory/GC/threads (`jvm_*`), HikariCP pool (`hikaricp_*` — from the JDBC datasource), Redis/Lettuce, Tomcat, and — worth a custom gauge — bucket4j rejections.

**Custom rate-limit metric:** in `RateLimitingFilter`, increment a counter when a request is throttled:
```java
// inject MeterRegistry; on 429:
meterRegistry.counter("app.ratelimit.rejected", "brand", brandOrUnknown).increment();
```
(Keep `brand` tag only if brand cardinality is bounded — see pitfalls.)

**Starter dashboard** — one Grafana dashboard, 4 rows:
1. **Golden signals (RED):** req/s (by method), error rate % (4xx and 5xx stacked), p50/p95/p99 latency. Single stat panels + time series.
2. **Per-endpoint table:** top URIs by request count, error rate, p95 — sortable, to spot the worst offender fast.
3. **JVM/runtime:** heap used vs max, GC pause time, live threads, CPU. (Watch thread count if virtual threads get enabled.)
4. **Dependencies & tenancy:** HikariCP active/idle/pending connections, Redis command latency, `app_ratelimit_rejected` rate, and (low-cardinality) requests split by `deployment.env`. A trace-to-logs data link on the latency panel (Grafana exemplars) jumps from a slow request to its trace.

Store the dashboard JSON at `.plans/02-observability/grafana-dashboard.json` when built (out of scope to author here; description above is the spec). Enable **exemplars** so latency histograms link straight to traces (requires the Prometheus registry exemplar support + tracing on the same request — already satisfied by this setup).

### Step 10 — Java 25 virtual threads + ThreadLocal MDC caveat

The app uses `ThreadLocal` for both `BrandContext` (`BrandContext.java:10`) and MDC. Today, requests run on pooled platform threads and everything works because the filter sets and clears context on the same thread.

**Caveat to encode now:**
- If you later set `spring.threads.virtual.enabled: true` (attractive on Java 25), each request still runs on a single carrier-mounted virtual thread, so filter-scoped MDC/BrandContext **still works** for the request thread.
- The breakage is with **child threads / async**: if request code spawns its own threads (`new Thread`, a raw `ExecutorService`, `CompletableFuture.supplyAsync` on the common pool), those children do **not** inherit MDC or `BrandContext`. Trace context also won't propagate unless the executor is context-aware.
- Mitigations to document in code (no action needed until async is introduced):
  1. Prefer Micrometer's `ContextSnapshot` / `ContextExecutorService` wrapping (from `context-propagation`, pulled in transitively by micrometer-tracing) to carry trace + MDC across thread hops.
  2. Or wrap tasks manually: capture `MDC.getCopyOfContextMap()` + `BrandContext.get()` before dispatch and restore in the child, clearing in `finally`.
  3. If enabling virtual threads, verify Redis/JDBC drivers don't pin carriers (Lettuce is non-blocking-friendly; the MySQL JDBC driver uses `synchronized` and can pin — measure before enabling in prod).
- Action for this plan: add a short `// NOTE:` comment in `CorrelationIdFilter` and `BrandContextFilter` pointing to this section, so the next person adding async work knows the propagation rule.

---

## 6. Wiring summary (what depends on what)

```
Frontend traceInterceptor  --traceparent-->  Backend
                                               │
CorrelationIdFilter (HIGHEST_PRECEDENCE+5) ── sets MDC.correlationId, echoes header
   │
Security chain: RateLimitingFilter -> BrandContextFilter (sets MDC.brand + BrandContext) -> auth
   │
Micrometer Tracing: reads traceparent (W3C), starts/continues span, injects traceId/spanId into MDC
   │
Request handler / services / CustomLogger / GlobalExceptionHandler
   │            log via SLF4J -> Spring Boot structured (ECS) console -> stdout JSON
   │            each line carries {traceId, spanId, correlationId, brand}
   ├── metrics recorded on http.server.requests timer -> Prometheus registry -> /actuator/prometheus (secured)
   └── span exported via OTLP -> Tempo/Honeycomb/Collector
```

Meter registry auto-configured by presence of `micrometer-registry-prometheus`. Tracing auto-configured by `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`. No `@Configuration` beans needed except the optional metrics scrape user (Step 3) and the optional rate-limit counter (Step 9).

---

## 7. Verification

Run `./gradlew :backEnd:bootRun` (env: `LOG_FORMAT` unset for readable local logs, or `ecs` to see JSON).

1. **Prometheus endpoint exists & is secured:**
   ```bash
   # Unauthenticated -> 401/403 (proves it's NOT public):
   curl -i http://localhost:8080/actuator/prometheus
   # With scrape creds -> 200 + text exposition:
   curl -s -u "$METRICS_USER:$METRICS_PASSWORD" http://localhost:8080/actuator/prometheus | grep http_server_requests
   ```
   Expect `http_server_requests_seconds_count{...}` and, with histograms on, `http_server_requests_seconds_bucket{le="..."}`.

2. **Health still public (regression check):**
   ```bash
   curl -s http://localhost:8080/actuator/health   # 200, no auth
   ```

3. **Trace IDs + brand + correlation id in logs:** make a request with a brand and a correlation id:
   ```bash
   curl -i -H 'X-App-Brand: acme' -H 'X-Correlation-Id: test-123' \
        -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' \
        http://localhost:8080/api/<some-endpoint>
   ```
   - Response has `X-Correlation-Id: test-123`.
   - Backend log line (JSON when `LOG_FORMAT=ecs`) contains `trace.id=4bf92f3577b34da6a3ce929d0e0e4736`, a `span.id`, `correlationId=test-123`, `brand=ACME`.

4. **Error path carries context:** trigger a handled exception; confirm the `GlobalExceptionHandler` → `CustomLogger` line in stdout has the same `trace.id`/`correlationId`/`brand` — proving MDC propagation without touching those classes.

5. **View a trace end-to-end:** with `OTLP_TRACES_ENDPOINT` pointed at Jaeger (`docker run -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one`) or Honeycomb, open a request from the running Angular app and confirm one trace spans frontend `traceparent` → backend server span → any outbound RestClient span.

6. **Grafana:** point Prometheus at the scrape endpoint (with basic_auth), import the dashboard, confirm RED panels populate under load (`hey`/`ab` a few hundred requests).

---

## 8. Best-practice notes & pitfalls

- **Metric cardinality (the #1 footgun).** Never tag metrics with unbounded values: no user id, no raw path with ids, no correlation id, no full URL. Micrometer's `uri` tag is already templated (`/users/{id}`) — keep it that way; never build a custom timer tagged with the concrete path. `brand` is acceptable *only if* the brand set is small and bounded (tens, not thousands). If brands can grow unbounded, drop the `brand` tag from metrics (keep it in logs/traces only). Each distinct tag-value combination is a separate time series — cardinality explosions kill Prometheus.
- **PII in logs.** JSON logs are shipped off-box. Do not log request bodies, `x-auth-token`, passwords, or full emails at INFO. Note `GlobalExceptionHandler.handleGeneralException` puts `ex.getMessage()` into the response (`GlobalExceptionHandler.java:78`) — audit that exception messages don't leak internals; the same applies to what `CustomLogger` writes. Consider a scrubbing step if any exception can contain user input.
- **Never expose metrics/trace endpoints publicly.** Confirmed here via Security rules (Step 3). `/actuator/prometheus` reveals endpoint inventory, error rates, JVM internals, and dependency topology — reconnaissance gold and a DoS amplifier. Keep them authenticated or on a private port.
- **Sampling.** 100% tracing in prod is expensive and high-volume. Head-based sampling via `management.tracing.sampling.probability` (0.05–0.2 typical). For debugging a specific issue, temporarily raise it, or add tail-based sampling at an OTel Collector (keep all error/slow traces, sample the rest).
- **Trace context must round-trip cleanly.** If CORS doesn't allow `traceparent` (Step 7), the browser silently drops it and traces won't join — verify with the curl in Verification step 3 and browser devtools network headers.
- **MDC leakage on pooled threads.** Every MDC.put MUST have a matching remove in `finally` (done in Steps 4/5). A missed clear on a platform thread pool leaks one request's context into the next request — worst-case a wrong `brand` on someone else's log line.
- **Virtual threads + JDBC pinning.** If you enable virtual threads, the MySQL driver's `synchronized` blocks can pin carriers under load. Measure with JFR (`jdk.VirtualThreadPinned`) before shipping. Unrelated to correctness of MDC but a real perf trap on this stack.
- **Don't double-instrument.** Adding both a Brave and OTel bridge, or OTel Java agent + micrometer-tracing-bridge-otel, causes duplicate/broken spans. Use exactly one: the micrometer-tracing OTel bridge chosen here. Do not attach the OTel Java auto-agent on Railway alongside this.
- **Structured logging vs local DX.** JSON logs are unreadable in a terminal. Keep the `application-local.yaml` blank-format override (Step 6) so local dev stays human-readable while prod ships JSON.
- **Endpoint `metrics` vs `prometheus`.** `/actuator/metrics` is a JSON drill-down for humans/debugging; `/actuator/prometheus` is the scrape format. Both are secured together in Step 3.

---

## 9. Effort estimate

| Task | Effort |
|---|---|
| Deps + BOM verification (Step 1) | 0.5h |
| Actuator exposure + metrics/tracing/otlp config (Step 2) | 1h |
| Security rules + scrape identity (Step 3) | 1.5h (incl. test that session auth still works) |
| CorrelationIdFilter (Step 4) | 0.5h |
| BrandContextFilter MDC change (Step 5) | 0.25h |
| Structured JSON logging + local override (Step 6) | 0.5h |
| Frontend traceparent interceptor + CORS (Step 7) | 1h |
| OTLP target wiring + env (Step 8) | 0.5h (excl. provisioning the backend) |
| RED metrics + rate-limit counter (Step 9, code) | 1h |
| Grafana starter dashboard JSON (Step 9, dashboard) | 2–3h |
| Verification pass end-to-end (Step 7 checks) | 1.5h |
| Virtual-thread caveat notes/comments (Step 10) | 0.25h |

**Core (metrics + tracing + structured logs, no dashboard):** ~1 day.
**Full including Grafana dashboard + collector provisioning:** ~2 days.

---

## 10. References

- Spring Boot 4 — Actuator metrics & Micrometer: `docs.spring.io/spring-boot` → "Metrics" (Prometheus registry auto-config, `management.endpoints.web.exposure.include`).
- Spring Boot 4 — Tracing: "Tracing" chapter (`micrometer-tracing-bridge-otel`, `management.tracing.*`, `management.otlp.tracing.endpoint`).
- Spring Boot 4 — Structured logging: "Structured Logging" (`logging.structured.format.console`, ECS / Logstash / GELF formats, MDC inclusion).
- Micrometer Tracing docs — logging correlation (`traceId`/`spanId` MDC injection), W3C propagation, `context-propagation` for thread hops.
- OpenTelemetry — OTLP exporter config; W3C Trace Context spec (`traceparent` format: `version-traceid-spanid-flags`).
- Prometheus — `histogram_quantile`, cardinality guidance; exemplars.
- Grafana — Tempo trace-to-logs correlation, exemplars, RED method (Tom Wilkie).
- Java 25 / JEP virtual threads — pinning, `ThreadLocal` inheritance semantics; Micrometer `ContextSnapshot`/`ContextExecutorService`.
- Repo files cited throughout: `backEnd/build.gradle`, `backEnd/src/main/resources/application.yaml`, `.../platform/security/SecurityConfig.java`, `.../platform/tenancy/BrandContext.java` + `BrandContextFilter.java`, `.../shared/logging/CustomLogger.java`, `.../shared/error/GlobalExceptionHandler.java`, `frontEnd/src/app/core/interceptors/brand.interceptor.ts`, `frontEnd/src/app/app.component.config.ts`.
