# Learning Guide — Nigerian Financial Calculator Project

A summary of problems encountered, solutions applied, and the reasoning behind each decision while building this project.

---

## 1. Angular Standalone Components (v20+)

**Problem:** Components had `standalone: true` explicitly set in `@Component` decorators, which caused rendering issues in Angular v20+.

**Solution:** Removed `standalone: true` from all component decorators.

**Why:** Starting from Angular v20, all components are standalone by default. Explicitly setting `standalone: true` is unnecessary and can cause unexpected behavior. The Angular team made this the default to reduce boilerplate — you no longer need to declare it.

---

## 2. Session Persistence on Page Refresh

**Problem:** After logging in and refreshing the browser, the user was kicked back to the landing page even though the `SESSION` cookie was still valid on the server.

**Solution:**
- Added a `GET /api/auth/me` endpoint on the backend that reads the authenticated user from the Spring Security session context
- Added a `checkSession()` method in the frontend `AuthService` that calls `/api/auth/me`
- Used `APP_INITIALIZER` to call `checkSession()` on app startup, blocking rendering until the check completes

**Why:** Angular signals (`currentUser = signal(null)`) reset to their initial value on every page load because JavaScript state doesn't survive a full page reload. The server still knows the user (via the `SESSION` cookie the browser sends automatically), but the Angular app has "amnesia." The `APP_INITIALIZER` pattern ensures the app re-hydrates auth state from the server before rendering anything — preventing a flash of the landing page. This is the same pattern used in production apps (e.g., Admin project uses `APP_INITIALIZER` + cookie token validation, Spok uses router `beforeEach` guard + backend `/component-data` API).

**Key Takeaway:** Never persist `isAuthenticated` in `localStorage` alone — always re-derive it from the backend. Both the Admin and Spok reference projects explicitly exclude auth state from client-side persistence.

---

## 3. 401 Session Expiry Handling

**Problem:** If a user's session expired while they were actively using the app, API calls would silently fail with 401 errors and the UI wouldn't react.

**Solution:** Created an HTTP interceptor (`auth.interceptor.ts`) that catches 401 responses on non-auth endpoints, clears the user session signal, and navigates to the landing page.

**Why:** The server can invalidate a session at any time (timeout, manual invalidation, server restart). Without a global 401 handler, the user sees broken UI instead of being gracefully redirected to login. The interceptor excludes `/api/auth/` endpoints because 401 on login/register is expected behavior (wrong credentials), not a session expiry.

---

## 4. `inject()` vs Constructor Injection

**Problem:** Uncertainty about whether constructor injection (`constructor(private readonly service: MyService)`) is still valid in Angular.

**Solution:** Both work. The project uses `inject()` function for consistency.

**Why:** Constructor injection is **not broken or deprecated** — it's fully supported. The `inject()` function (introduced in Angular 14) is preferred in modern Angular because:

| Aspect | Constructor Injection | `inject()` Function |
|---|---|---|
| Field initializers | ❌ Can't use injected deps (they run before constructor body) | ✅ Works directly |
| Inheritance | Subclasses must call `super()` and forward all parent deps | Subclasses inherit automatically |
| Boilerplate | More verbose with many deps | Less verbose |
| Signal compatibility | Requires extra steps for field-level signal derivation | Natural fit with `computed()` and signals |

The critical advantage: with `inject()`, you can write `readonly isLoggedIn = this.authService.isLoggedIn` as a field initializer. With constructor injection, field initializers run before the constructor body, so `this.authService` would be `undefined`.

---

## 5. SCSS Architecture: Mixins (`_shared.scss`) vs Global Classes (`styles.scss`)

**Problem:** Component SCSS files had massive duplication — the same button, input, and message styles were copy-pasted across multiple components. Initially, a `_shared.scss` file with mixins was created, but this still generated duplicate CSS in every component that used `@include`.

**Solution:** Moved all reusable UI patterns (buttons, inputs, messages, gradient text, modal overlay) into **global CSS classes** in `styles.scss` and deleted `_shared.scss`. Component SCSS files now contain only layout-specific styles.

**Why:**

| Approach | Scoped? | Duplicate CSS? | Bundle Impact |
|---|---|---|---|
| Copy-paste per component | ✅ Scoped | ❌ Massive duplication | Bloated |
| Mixins (`_shared.scss`) | ✅ Scoped | ❌ Still duplicated (each `@include` generates a new copy) | Bloated |
| Global classes (`styles.scss`) | ❌ Global | ✅ One copy | Smallest |

Since no component uses `ViewEncapsulation.ShadowDom`, global classes from `styles.scss` are accessible in every component template via Angular's default `Emulated` encapsulation. Component SCSS should only contain **layout and structural** rules — anything reusable belongs in the global stylesheet.

**Result:** ~284 lines of CSS removed across component files. Component SCSS became purely structural.

---

## 6. SCSS Import Path Resolution

**Problem:** `@use 'styles/shared'` worked during `ng build` (because `stylePreprocessorOptions.includePaths` was configured in `angular.json`) but failed in the IDE.

**Solution:** Switched to relative paths: `@use '../../../styles/shared'` which works in both Angular CLI and the IDE.

**Why:** `stylePreprocessorOptions.includePaths` in `angular.json` configures the Sass compiler used by Angular CLI, but IDEs (IntelliJ, VS Code) use their own Sass language server which doesn't read `angular.json`. Relative paths are universally resolved by all tools.

---

## 7. MySQL `BigInteger` Cast Error on Auto-Generated Keys

**Problem:** Registration failed with `DataRetrievalFailureException: Unable to cast [java.math.BigInteger] to [java.lang.Long]` when saving a new user.

**Solution:** Changed `keyHolder.getKeyAs(Long.class)` to `keyHolder.getKey().longValue()` in the repository's `save()` method.

**Why:** MySQL's JDBC Connector/J (8.x+) returns auto-generated keys (`AUTO_INCREMENT` on `BIGINT` columns) as `java.math.BigInteger`, not `java.lang.Long`. Spring's `getKeyAs(Long.class)` attempts a direct cast which fails because `BigInteger` doesn't extend `Long`. Using `getKey()` returns a `java.lang.Number` (common superclass), and `.longValue()` is defined on `Number` — so it works regardless of the concrete type.

---

## 8. Netty DNS Resolver Warning on macOS

**Problem:** Spring Boot startup showed: `Can not find io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider in the classpath, fallback to system defaults.`

**Solution:** Added `runtimeOnly 'io.netty:netty-resolver-dns-native-macos::osx-aarch_64'` to `build.gradle` dependencies.

**Why:** Spring Session with Redis uses Lettuce (the default Redis client), which depends on Netty. On macOS, Netty needs a platform-specific native DNS resolver to correctly resolve addresses using the macOS DNS stack. Without it, Netty falls back to system defaults which can produce incorrect DNS resolutions. The `runtimeOnly` scope is correct because this dependency is only needed at runtime (it's a native `.dylib` loaded via JNI) and has zero impact on Linux/Docker.

---

## 9. Spring Security Session Context Restoration

**Problem:** The `GET /api/auth/me` endpoint returned 401 even when the `SESSION` cookie was valid — Spring Security wasn't restoring the `SecurityContext` from the session.

**Solution:** Added `.securityContext(sc -> sc.securityContextRepository(new HttpSessionSecurityContextRepository()))` to the `SecurityConfig` filter chain.

**Why:** Spring Security needs to be explicitly told to use `HttpSessionSecurityContextRepository` to restore the `SecurityContext` from the HTTP session on subsequent requests. Without this, each request starts with an empty/anonymous security context even if a valid session exists. This is the bridge between the `SESSION` cookie and Spring Security's `Authentication` object.

---

## 10. Docker Compose: Image Not Found (Buildpacks)

**Problem:** `docker compose up` failed with `failed to resolve reference "docker.io/library/ngn-calc-backend:latest"` — Docker tried to pull the backend image from Docker Hub.

**Solution:** Build the image locally first with `./gradlew bootBuildImage`, then run `docker compose up`.

**Why:** When `docker-compose.yml` specifies `image: ngn-calc-backend:latest` without a `build:` directive, Docker Compose assumes the image already exists locally or on a remote registry. Since the image is built via Spring Boot Buildpacks (not a Dockerfile), it must be pre-built. The alternative is to restore the `build: context:` directive with a Dockerfile for a unified `docker compose up --build` workflow.

---

## 11. Docker Compose: Variable Substitution vs Container Environment

**Problem:** `docker compose config` warned: `The "MYSQL_DATABASE" variable is not set. Defaulting to a blank string.`

**Solution:** Either escape with `$$MYSQL_DATABASE` (so Docker Compose passes it literally to the container shell) or hardcode the value in the healthcheck.

**Why:** Docker Compose performs variable substitution on the **host side** before the container runs. `$MYSQL_DATABASE` in a healthcheck command is interpreted as a host environment variable, not the container's `MYSQL_DATABASE` env var. Using `$$` escapes the `$` so the container shell resolves it at runtime.

---

## 12. Docker Compose Profiles

**Problem:** Running `docker compose up` started all services (MySQL, Redis, backend, frontend) even when only the database was needed for local development.

**Solution:** Added `profiles` to services — infrastructure (MySQL, Redis) has no profile (always starts), backend has `[backend, full]`, frontend has `[full]`.

**Why:** Docker Compose profiles let you tag services for selective startup:
- `docker compose up -d` → only MySQL + Redis (for local dev with IDE)
- `docker compose --profile backend up -d` → MySQL + Redis + Backend
- `docker compose --profile full up -d` → everything

Services without a `profiles` key always start. This documents intended service groupings directly in the file.

---

## 13. Spring Boot Docker Images: Dockerfile vs Buildpacks

**Problem:** Deciding between a manual `Dockerfile` and Spring Boot's built-in `bootBuildImage` (Buildpacks) for creating Docker images.

**Solution:** Used `bootBuildImage` (Spring Boot Buildpacks) as the primary approach, keeping the manual Dockerfile as `Dockerfile.manual` for reference.

**Why:**

| Aspect | Manual Dockerfile | `bootBuildImage` (Buildpacks) |
|---|---|---|
| Maintenance | You maintain the Dockerfile | Spring Boot maintains it |
| Layer optimization | Manual `COPY --from` layering | Automatic optimized layers |
| JVM memory tuning | Manual `-Xmx` flags | Automatic memory calculator |
| Security | Manual non-root user setup | Built-in non-root execution |
| Build cache | Manual layer caching | Automatic dependency layer caching |
| Extra plugin needed | No | No (built into `org.springframework.boot` plugin) |

Buildpacks is the officially recommended approach for Spring Boot. The tradeoff is that `docker compose up --build` can't trigger it directly — you need a separate `./gradlew bootBuildImage` step.

---

## 14. Angular View Encapsulation and Global Styles

**Reference:** Angular uses `ViewEncapsulation.Emulated` by default. This means:
- Styles in component `.scss` files are **scoped** — Angular rewrites selectors with unique attributes like `_ngcontent-abc123`
- Styles in `styles.scss` (global stylesheet) are **not scoped** — they apply to raw HTML elements and CSS classes across the entire app
- Global classes **are accessible** in component templates without any special configuration
- Component-scoped styles **cannot** be accessed outside their component

**When to use global styles:** Design-system tokens (CSS custom properties), reusable utility classes (`.btn-primary`, `.text-input`, `.message-error`), layout primitives (`.glass-card`, `.toggle-group`)

**When to use component styles:** Layout and positioning specific to one component, component-specific animations, structural rules that only make sense within that component's template

---

## 15. Angular Router + Lazy Loading (Code Splitting)

**Problem:** The app used signal-based tab navigation — no URL routing, no deep-linking, no browser back/forward, and the entire app loaded as one chunk.

**Solution:** Added Angular Router with lazy-loaded routes (`loadComponent`) for `/`, `/calculator`, `/tax`, `/scenarios`, plus a `CanActivateFn` auth guard.

**Why:** Lazy loading with `loadComponent` enables automatic code splitting — each route becomes a separate chunk loaded on demand. This reduced the initial bundle from 506KB to 355KB (30% reduction). URL routing also enables deep-linking, browser history navigation, and proper SEO. The auth guard (`CanActivateFn`) checks `AuthService.isLoggedIn()` and redirects unauthenticated users to `/`.

---

## 16. Glassmorphism Modal Pattern

**Reference:** The auth modal uses a "glassmorphism" design pattern:
- **Fixed overlay** with `backdrop-filter: blur(8px)` dims and blurs the background
- **Glass card** sits centered within the overlay
- **Accessibility requires:** `role="dialog"`, `aria-modal="true"`, focus trapping (`cdkTrapFocus`), `Escape` key close, and focus restore to the trigger element on close
- **Close behavior:** clicking outside the card (on the overlay) closes the modal — implemented via `(click)` on overlay with `$event.stopPropagation()` on the card

---

## 17. Auth Modal Accessibility

**Problem:** The auth modal lacked focus trapping (Tab key could escape), keyboard close (Escape), and focus restoration.

**Solution:**
- Installed `@angular/cdk` for `cdkTrapFocus` and `cdkTrapFocusAutoCapture` directives
- Added `(keydown.escape)="close()"` on the overlay
- Captured `document.activeElement` on modal open, restored focus to it on close

**Why:** WCAG AA compliance requires that modal dialogs trap focus within them (users can't Tab to elements behind the overlay), support keyboard dismissal, and return focus to the trigger element when closed. Without these, keyboard and screen reader users can't effectively use the modal.

---

## 18. Redis Session Storage — Default Database

**Reference:** Spring Session with Redis stores sessions in **database index `0`** by default (when `spring.data.redis.database` is not configured). The session keys are prefixed with the namespace configured in `application.yaml` (`spring:session:ngn_calc`). Redis supports databases `0` through `15`. Using the `namespace` property provides logical separation within the same database via key prefixing — this is the more common and recommended approach over using separate database indexes.

---

## 19. Logout + Client-Side Navigation

**Problem:** After clicking logout, the user's session was cleared but they still saw the protected view (e.g., `/calculator`) instead of being redirected.

**Solution:** Added `this.router.navigate(['/'])` inside the `logout()` subscribe callback in `NavbarComponent`.

**Why:** `AuthService.logout()` clears the `currentUser` signal and calls the backend, but it doesn't control navigation. The component that triggers logout is responsible for redirecting the user. Without the navigation call, Angular's router stays on the current route — even though the auth guard would block it on a fresh navigation, it doesn't retroactively eject users from already-rendered routes.

---

## 20. `APP_INITIALIZER` Pattern

**Reference:** `APP_INITIALIZER` is an Angular injection token that accepts factory functions executed during app bootstrap. Angular **blocks rendering** until all initializers complete. This is useful for:
- Checking auth session before showing any UI (prevents landing page flash)
- Loading configuration from a server
- Fetching user preferences or brand settings

```typescript
{
  provide: APP_INITIALIZER,
  useFactory: (authService: AuthService) => () => firstValueFrom(authService.checkSession()),
  deps: [AuthService],
  multi: true  // allows multiple initializers
}
```

**Caution:** If the initializer's HTTP call is slow or fails without a timeout, the app hangs on a blank screen. Always include `catchError` in the observable and consider adding a timeout.

---

## 21. Session Persistence Patterns Compared

**Reference:** From analyzing the Admin (Angular + NgRx) and Spok (Vue 3 + Pinia) projects:

| Pattern | How | Pros | Cons |
|---|---|---|---|
| `GET /api/me` + `APP_INITIALIZER` | Backend endpoint validates session cookie; blocks app render until check completes | Server is source of truth; no stale state | Extra HTTP request on every page load |
| `localStorage` + background validation | Restore user from `localStorage` immediately; validate with server in background | Instant UI; no flash | Can show stale/wrong state briefly |
| JWT tokens | Stateless auth; token stored client-side | Scales horizontally; no server session store | Major backend refactor; token refresh complexity |
| Cookie with user info | Server sets a non-httpOnly cookie with display data | No extra HTTP call | Cookie size limits; XSS readable |

**Best practice from both projects:** Never persist `isAuthenticated` in client-side storage — always re-derive it from the backend on page load.

---

## 22. SVG Backgrounds and Theme Matching

**Problem:** Custom SVG backgrounds didn't match the app's theme colors and stars/particles were invisible (opacities too low).

**Solution:** Updated SVG base fills to match CSS theme variables (`#0f172a` for dark, `#f1f5f9` for light), boosted star/particle opacities 2-3x, and updated accent colors to match `--primary`.

**Why:** SVG backgrounds must use the exact same color palette as the CSS theme to avoid visual mismatch. Very low opacity values (0.05-0.10) that look fine in an SVG editor become invisible against a complex UI. The `--bg-color` CSS variable must match the SVG's `<rect>` fill so there's no color seam during loading.

---

## 23. Form Validation UX

**Problem:** Login and register forms showed no feedback when submitted with invalid data.

**Solution:** Added `markAllAsTouched()` on submit when forms are invalid, inline `@if` validation messages under each field, and `[class.input-invalid]` + `[attr.aria-invalid]` bindings.

**Why:** Reactive forms in Angular don't mark controls as `touched` until the user blurs them. Calling `markAllAsTouched()` on submit makes all validation errors visible immediately. The `aria-invalid` and `aria-describedby` attributes are required for screen readers to announce validation errors — this is a WCAG AA requirement.

---

## 24. SonarCloud vs Checkstyle

**Reference:** SonarCloud (free for all projects) replaces Checkstyle entirely and provides significantly more value:

| Feature | Checkstyle | SonarCloud |
|---|---|---|
| Style rules | ✅ | ✅ |
| Bug detection | ❌ | ✅ |
| Vulnerability detection | ❌ | ✅ |
| Code coverage tracking | ❌ | ✅ (with JaCoCo) |
| Duplication detection | ❌ | ✅ |
| PR decoration | ❌ | ✅ |
| TypeScript support | ❌ | ✅ |
| Self-hosting required | No | No |

Integration: Backend uses the `org.sonarqube` Gradle plugin + `jacoco` for coverage. Frontend uses the `SonarSource/sonarcloud-github-action` GitHub Action + `lcov` coverage reports.

---

## 25. Free-Tier Deployment Options

**Reference:**

| Platform | Frontend | Backend | Database | Redis | Credit Card |
|---|---|---|---|---|---|
| **Render + Aiven + Upstash** | Free static site (never sleeps) | Free web service (sleeps after 15min) | Aiven MySQL free (1GB) | Upstash free (10K cmds/day) | ❌ Not required |
| **Railway** | $5/mo credit covers all | Built-in | Built-in MySQL | Built-in Redis | ❌ Not required |
| **Fly.io** | 3 free VMs | Free VM | External (Aiven) | External (Upstash) | ✅ Required |

**Recommended:** Render + Aiven + Upstash for the best free-tier experience — frontend never sleeps, no credit card needed, auto-deploy from GitHub.

---

## 26. `withCredentials: true` for Cookie-Based Auth

**Reference:** When using session-based authentication with cookies, every HTTP request from the Angular frontend must include `withCredentials: true`:

```typescript
this.http.post<AuthUser>(url, body, { withCredentials: true })
```

**Why:** By default, browsers don't send cookies on cross-origin requests (during development, Angular runs on `localhost:4200` and the backend on `localhost:8080`). `withCredentials: true` tells the browser to include cookies (like the `SESSION` cookie) with the request. Without it, the backend never receives the session cookie and treats every request as unauthenticated.

**In production:** If frontend and backend are on the same origin (nginx proxies `/api` to the backend), `withCredentials` is technically unnecessary — but keeping it ensures the code works in both dev and prod environments.

---

## 27. HTTP Interceptors in Angular

**Reference:** Angular HTTP interceptors are middleware functions that process every HTTP request/response. This project uses two:

1. **Brand Interceptor** (`brandInterceptor`): Adds `X-App-Brand: NGN` header to every request so the backend's `BrandContextFilter` can identify the brand context.

2. **Auth Interceptor** (`authInterceptor`): Catches 401 responses on non-auth endpoints, clears the user session, and redirects to login.

Interceptors are registered in order via `provideHttpClient(withInterceptors([brandInterceptor, authInterceptor]))`. Request interceptors execute in order (brand first, then auth). Response interceptors execute in reverse order.

---

## 28. ESLint + Prettier Configuration for Angular

**Problem:** The initial `.eslintrc.json` was bloated with redundant stylistic rules (e.g., `@stylistic/indent`, `@stylistic/quotes`, `comma-dangle`) that conflicted with Prettier, and included deprecated settings like `createDefaultProgram: true`.

**Solution:** Stripped the ESLint config down to logic-only rules (`@angular-eslint/recommended`, `@typescript-eslint/recommended`, `unused-imports/no-unused-imports`) and added `"prettier"` as the last `extends` entry to disable all formatting-related rules.

**Why:** ESLint and Prettier have different responsibilities — ESLint catches bugs and enforces patterns; Prettier handles formatting (tabs, quotes, semicolons). When both try to control formatting, the editor flip-flops between "fixes." Using `eslint-config-prettier` as the final extension disables every ESLint rule that Prettier already handles, giving you a single source of truth for code style.

**Key Takeaway:** Keep `.eslintrc.json` focused on **logic** and `.prettierrc` focused on **appearance**. Never configure `indent`, `quotes`, or `semi` in ESLint if Prettier is active.

---

## 29. TypeScript Path Aliases (`@app/*`)

**Problem:** Deep imports like `../../../app.config` were hard to read and fragile when moving files between directories.

**Solution:** Added `baseUrl: "./"` and `paths: { "@app/*": ["src/app/*"] }` to `tsconfig.json`, enabling imports like `import { appConfig } from '@app/app.config'`.

**Why:** Path aliases provide three benefits: (1) **Readability** — it's immediately clear the import is from the app root. (2) **Refactoring safety** — moving a file deeper into a folder doesn't break imports across the codebase. (3) **Consistency** — all files reference the same `@app/` root regardless of their own location. Most IDEs (IntelliJ, WebStorm) recognize `tsconfig.json` paths automatically and offer alias-based auto-imports.

---

## 30. Vite SSR Cache Invalidation (`ERR_OUTDATED_OPTIMIZED_DEP`)

**Problem:** After adding new imports (e.g., `@angular/forms`, `chart.js/auto`), the dev server crashed with `ERR_OUTDATED_OPTIMIZED_DEP` — Vite's dependency optimizer detected a new dependency but the SSR runner was still using the old cached bundle.

**Solution:** Stop the server, run `rm -rf .angular/cache`, then restart with `npm start`.

**Why:** Vite pre-bundles dependencies for performance. When a new dependency appears (or an import path changes like `chart.js` → `chart.js/auto`), Vite re-optimizes in the background. In SSR mode, a race condition occurs: the server-side renderer tries to load the old `@angular/core` bundle while the new one is being generated. Clearing the `.angular/cache` directory forces a clean re-bundle from scratch.

**Key Takeaway:** Whenever you `npm install` a new package or change a major import path, restart the dev server and clear the cache preemptively.

---

## 31. Chart.js + Angular SSR (`NotYetImplemented` Error)

**Problem:** Chart.js threw `NotYetImplemented` at `HTMLCanvasElement` because the server-side rendering environment (Node.js) has no real DOM or Canvas API.

**Solution:** Used `afterNextRender()` from `@angular/core` to initialize Chart.js only in the browser, combined with `isPlatformBrowser` as a safety guard.

**Why:** `afterNextRender()` is guaranteed to **never** execute on the server — it only fires after the first browser render. This is cleaner than `ngAfterViewInit` + `isPlatformBrowser` because `ngAfterViewInit` still runs during SSR (Angular just doesn't have a real DOM). For any browser-only API (Canvas, WebGL, `window`, `localStorage`), `afterNextRender` is the modern Angular (v17+) best practice.

```typescript
constructor() {
  afterNextRender(() => {
    this.initChart(); // Safe — only runs in the browser
  });
}
```

---

## 32. Angular `effect()` Injection Context (`NG0203`)

**Problem:** Calling `effect()` inside an `afterNextRender` callback threw `NG0203: effect() can only be used within an injection context`.

**Solution:** Moved the `effect()` call into the constructor (which is an injection context) and used a null guard (`if (!this.chart) return;`) to wait for the chart to be initialized.

**Why:** Angular's `effect()` must be created during the component's initialization phase (constructor, field initializer) or within an explicit `runInInjectionContext()` call. By the time `afterNextRender` executes, the constructor's synchronous injection context has ended. The pattern is: create the effect in the constructor (it starts tracking immediately), but guard the body so it only does real work once the chart exists.

```typescript
constructor() {
  // Effect created in injection context — safe
  effect(() => {
    const chart = this.chart;
    if (!chart) return; // Skip until chart is initialized
    chart.data.labels = this.chartLabels();
    chart.update();
  });

  afterNextRender(() => this.initChart());
}
```

---

## 33. Angular Signal Tracking in `effect()` — Synchronous Reads Only

**Problem:** The chart wasn't updating when sliders were moved, even though the `effect()` contained signal reads.

**Solution:** Ensured all signal reads happen **synchronously** inside the `effect()` body — not inside `setTimeout`, `Promise.then`, or delegated methods.

**Why:** Angular's `effect()` only tracks signals that are read **synchronously** during its execution. If you read a signal inside a `setTimeout` or a `.then()` callback, the tracking context is gone and Angular doesn't know to re-run the effect when that signal changes. This is the most common "gotcha" with Angular effects.

```typescript
// ❌ BROKEN — signals read inside async callback, not tracked
effect(() => {
  Promise.resolve().then(() => {
    chart.data.labels = this.chartLabels(); // Not tracked!
  });
});

// ✅ CORRECT — signals read synchronously, then used
effect(() => {
  const labels = this.chartLabels();       // Tracked
  const invested = this.investedData();    // Tracked
  chart.data.labels = labels;
  chart.data.datasets[0].data = invested;
  chart.update();
});
```

---

## 34. SSR vs SPA Decision for Simple Apps

**Problem:** The Compound Interest calculator suffered from repeated hydration race conditions with Chart.js, `afterNextRender` timing issues, and `ERR_OUTDATED_OPTIMIZED_DEP` errors — all caused by SSR.

**Solution:** Removed SSR entirely by setting `"outputMode": "static"` in `angular.json` and deleting `main.server.ts`, `server.ts`, `app.config.server.ts`, and `app.routes.server.ts`.

**Why:** SSR is valuable for content-heavy, SEO-critical apps (blogs, e-commerce product pages). For a **single-page interactive calculator** with no crawlable content and heavy Canvas/Chart.js usage, SSR adds complexity with zero benefit:

| Factor | SSR | SPA |
|---|---|---|
| SEO | ✅ Critical for content sites | ❌ Not needed for calculators |
| First paint | Faster (server-rendered HTML) | Slightly slower (JS must execute) |
| Canvas/WebGL | ❌ Requires `afterNextRender` workarounds | ✅ Just works |
| Build time | Slower (two bundles) | Faster (one bundle) |
| Developer experience | Complex hydration debugging | Simple |

**Key Takeaway:** Don't use SSR by default. Evaluate whether your app actually benefits from server-rendered HTML before opting in.

---

## 35. Smart/Dumb Component Architecture

**Problem:** Everything was in a single `AppComponent` — state, calculations, chart logic, form inputs, and styling — making it hard to maintain or extend.

**Solution:** Extracted the UI into presentational ("Dumb") components (`InvestmentFormComponent`, `SummaryMetricsComponent`, `InvestmentChartComponent`, `MilestonesComponent`, `YearBreakdownComponent`) and kept `AppComponent` as the orchestrating ("Smart") component that owns state and computed signals.

**Why:**
- **Dumb components** receive data via `input()` and emit events via `output()`. They have no business logic — they just render what they're given. This makes them reusable and easy to test.
- **Smart components** own the application state (signals), computed derivations, and service interactions. They pass data down and handle events up.
- **Separation of concerns** means you can change the chart library without touching the form, or redesign the form layout without affecting calculations.

---

## 36. Centralized Configuration Constants

**Problem:** Magic numbers (tax rates, default values, milestone thresholds) were scattered across components and utility functions, making updates error-prone.

**Solution:** Created `app.constants.ts` as the single source of truth for all business rules and static configuration — defaults, tax bands, frequency options, range limits, theme thresholds, and milestone targets.

**Why:** When the Nigerian government changes tax bands (e.g., in 2028), you update **one file** instead of hunting through components, utilities, and templates. Named constants (`NIGERIA_WHT_RATE` vs `0.10`) also communicate intent — a developer reading the code instantly understands what the value represents.

---

## 37. CSS Custom Properties for Dynamic Theming

**Problem:** Hardcoded hex colors throughout component SCSS files made it impossible to support multiple themes without duplicating every style rule.

**Solution:** Defined all colors as CSS custom properties (`--bg-color`, `--text-color`, `--primary`, `--secondary`, `--card-bg`, etc.) in `styles.scss`, then overrode them using `[data-theme="dark"]` and `[data-theme="light"]` attribute selectors.

**Why:** CSS custom properties cascade through the DOM. By setting them on `<html>` via `document.documentElement.setAttribute('data-theme', 'dark')`, every element in the app instantly picks up the new values — no JavaScript re-render needed. Components reference `var(--primary)` instead of `#10b981`, and the `ThemeService` only needs to toggle one attribute.

```scss
:root { --bg-color: #ffffff; --text-color: #1e293b; }
[data-theme='dark'] { --bg-color: #0f172a; --text-color: #e2e8f0; }

// Components just use:
body { background: var(--bg-color); color: var(--text-color); }
```

---

## 38. Time-Based Theme Intensity System

**Problem:** A static Day/Night toggle felt flat — the user wanted accent colors to shift automatically based on the time of day.

**Solution:** Added a `TimeIntensity` system in `ThemeService` that calculates the current "intensity" (Cool/Midnight 0–6AM, Warm/Morning 6AM–12PM, Hot/Noon 12–3PM, Normal/Evening 3PM–12AM) and sets a `data-intensity` attribute on `<html>`. CSS custom properties `--primary` and `--secondary` are then overridden per intensity.

**Why:** This creates a dynamic, "living" UI without any user interaction. The logic lives entirely in the service (checked every 60 seconds), and the visual changes are driven purely by CSS variable overrides — components don't need to know about it. The intensity labels are internal (hidden from the user) — the toggle only shows "Day" or "Night."

---

## 39. Compound Interest Formula with Periodic Contributions

**Reference:** The app uses the standard compound interest formula extended with a future value of an annuity for periodic contributions:

```
A = P × (1 + r/n)^(n×t) + PMT × [((1 + r/n)^(n×t) - 1) / (r/n)]
```

Where:
- `P` = Principal (initial investment)
- `r` = Annual interest rate (decimal)
- `n` = Compounding periods per year (12 monthly, 4 quarterly, 2 bi-annually, 1 annually)
- `t` = Time in years
- `PMT` = Periodic contribution amount

**Edge case:** When `r = 0` (no interest), the annuity formula divides by zero. The implementation handles this: `r > 0 ? PMT * ((Math.pow(1+r, n*t) - 1) / r) : PMT * n * t`.

---

## 40. Nigeria Progressive Tax — Forward and Reverse Calculation

**Reference:** The Nigeria Tax Act 2025 (effective Jan 1, 2026) uses progressive bands:

| Band | Limit | Rate |
|---|---|---|
| First ₦800,000 | ₦800,000 | 0% (Exempt) |
| Next ₦2,200,000 | ₦2,200,000 | 15% |
| Next ₦9,000,000 | ₦9,000,000 | 18% |
| Next ₦13,000,000 | ₦13,000,000 | 21% |
| Next ₦25,000,000 | ₦25,000,000 | 23% |
| Above ₦50,000,000 | ∞ | 25% |

**Forward (Income → Tax):** Walk through bands, taxing `min(remaining_income, band_limit)` at each rate. **Reverse (Tax → Income):** Walk through bands, converting tax back to income using `tax_in_band / rate`. The exempt band (0%) is special — for reverse calculation, always add its full limit to the gross income since you can earn that much tax-free.

**Key Takeaway:** A ₦0 tax target returns ₦800,000 gross income (the maximum tax-free threshold), not ₦0.

---

## 41. Separating Types, Utils, and Constants

**Problem:** Business logic (math formulas), type definitions, and configuration values were all mixed into `AppComponent`, making the file over 300 lines and hard to navigate.

**Solution:** Created three dedicated files:
- `app.types.ts` — Interfaces (`CalculationResult`, `PITResult`, `YearBreakdown`, `Milestone`, `TaxBand`, etc.)
- `app.utils.ts` — Pure functions (`calculateFutureBalance`, `calculateDetailedPIT`, `calculateIncomeFromTax`)
- `app.constants.ts` — All static values (defaults, tax bands, frequency options, range limits)

**Why:** Pure functions in `app.utils.ts` are trivially unit-testable (no Angular dependencies). Types in `app.types.ts` serve as the contract between components and can be shared with a backend. Constants in `app.constants.ts` are the single source of truth for business rules. This separation follows the Single Responsibility Principle and makes the codebase navigable for new developers.

---

## 42. Project Folder Restructuring for Full-Stack

**Problem:** The Angular project occupied the root directory, leaving no space for a backend project alongside it.

**Solution:** Renamed the project root to `nigerian-financial-calculator` and moved the entire Angular app into a `frontEnd/` subdirectory, creating space for a `backEnd/` directory for the Spring Boot API.

**Why:** A monorepo-style folder structure (`frontEnd/`, `backEnd/`, `doc/`) keeps both halves of the application together in one repository while maintaining clear boundaries. Each sub-project has its own build tool (`package.json` for Angular, `build.gradle` for Spring Boot) and can be developed, tested, and deployed independently.

```
nigerian-financial-calculator/
├── frontEnd/          # Angular 21 SPA
│   ├── src/app/
│   ├── angular.json
│   └── package.json
├── backEnd/           # Spring Boot API
│   ├── src/main/java/
│   └── build.gradle
└── doc/               # Documentation
    └── learning_guides.md
```

---

*Last updated: April 2026*
