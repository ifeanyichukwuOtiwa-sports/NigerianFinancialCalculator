# Nigerian Financial Calculator - Technical Summary

## 1. Project Overview
A modern Angular-based financial application designed for the Nigerian market. It provides two primary tools:
1.  **Compound Interest Calculator**: Visualizes long-term wealth growth with support for various compounding frequencies and interactive milestones.
2.  **Tax Calculator**: Implements the **Nigeria Tax Act 2025** (effective Jan 1, 2026) progressive Personal Income Tax (PIT) bands and Withholding Tax (WHT) rules.

## 2. Technology Stack
-   **Framework**: Angular v21+ (Standalone Components, Signals API).
-   **State Management**: Fully reactive using **Angular Signals**.
-   **Visualization**: **Chart.js** (Standardized via `chart.js/auto`).
-   **Styling**: SCSS with a custom **Glassmorphism** theme system and CSS variables.
-   **Build Tool**: Angular CLI / Vite.

## 3. Architecture & Design Patterns
The application follows a modular, decoupled architecture:
-   **Smart/Dumb Component Pattern**: `AppComponent` acts as the orchestrator (Smart), while sub-components in `src/app/components/` handle presentation (Dumb).
-   **Centralized Configuration**: All business rules (tax bands, default values, compounding frequencies) are stored in `src/app/app.constants.ts`.
-   **Pure Functional Logic**: All mathematical formulas are isolated in `src/app/app.utils.ts` for testability and portability.
-   **Layout Separation**: Core layout elements like the `NavbarComponent` are decoupled from functional components and reside in `src/app/layout/`.
-   **Reactive Navigation**: Tab state is managed by `NavigationService`, allowing components to react to navigation changes without complex input/output chains.

## 4. Key Business Logic
### Financial Formulas
The application implements two primary mathematical systems:

#### 1. Compound Interest (with periodic contributions)
$$A = P \left(1 + \frac{r}{n}\right)^{nt} + PMT \times \frac{\left(1 + \frac{r}{n}\right)^{nt} - 1}{\frac{r}{n}}$$

Where:
-   **$A$**: Final Gross Balance (Principal + Interest + Contributions).
-   **$P$**: Initial Principal investment.
-   **$r$**: Annual interest rate (expressed as a decimal, e.g., $0.08$ for $8\%$).
-   **$n$**: Number of compounding periods per year (Monthly=12, Quarterly=4, Bi-Annually=2, Annually=1).
-   **$t$**: Total duration in years.
-   **$PMT$**: Periodic contribution amount (matches the frequency $n$).
-   **Interest Earned ($I$)**: $A - (P + PMT \times nt)$.

#### 2. Nigeria Tax Calculations (2026 Rules)
Based on the **Nigeria Tax Act 2025** (effective Jan 1, 2026):

**Withholding Tax (WHT):**
-   Flat **10%** deducted from the Total Interest earned.
-   **Net Interest**: $I \times 0.90$.

**Progressive Personal Income Tax (PIT):**
The tax is calculated by splitting the annual income (or interest) into specific bands:
-   **First ₦800,000**: 0% (Exempt)
-   **Next ₦2,200,000**: 15%
-   **Next ₦9,000,000**: 18%
-   **Next ₦13,000,000**: 21%
-   **Next ₦25,000,000**: 23%
-   **Above ₦50,000,000**: 25%

**Tax to Income (Reverse Extrapolation):**
Calculates the required Gross Income to pay a specific target Net Tax by iteratively reversing the bands above, correctly identifying the **₦800,000** tax-free threshold.

### Theme & Intensity System
Managed by `ThemeService`, the app features a dual-layer theme:
1.  **Visual Mode**: 3-way toggle (Light, Dark, System).
2.  **Time Intensity**: Accent colors automatically shift based on the current hour:
    -   **Midnight** (0-6): Cool Cyan/Indigo.
    -   **Morning** (6-12): Warm Amber/Orange.
    -   **Noon** (12-15): High-intensity Violet/Indigo.
    -   **Evening** (15-0): Standard Emerald/Blue.

## 5. Directory Structure
```text
src/app/
├── components/         # Presentational (Dumb) components
├── layout/             # Shell/Layout components (Navbar)
├── services/           # State & Logic services (Theme, Navigation)
├── app.constants.ts    # Single source of truth for business rules (Bands, Max Limits)
├── app.types.ts        # Shared TypeScript interfaces
└── app.utils.ts        # Pure mathematical functions
```

## 6. How to Extend
-   **Add New Tax Rules**: Update `NIGERIA_PIT_BANDS_2026` in `app.constants.ts`.
-   **New Financial Instrument**: Add a strategy to `TaxStrategy` type and update `calculateFutureBalance` in `app.utils.ts`.
-   **UI Changes**: All colors are controlled via CSS variables in `src/styles.scss`. Update the `:root` tokens to change the entire app's look.
-   **Adjust Range Limits**: Modify `MAX_PRINCIPAL` or `MAX_ANNUAL_INCOME` in `app.constants.ts`.

## 7. Performance & Optimization
-   **No-SSR Implementation**: The app is built as a standard SPA to avoid hydration race conditions with Chart.js.
-   **Signal-Based Reactivity**: Only the specific parts of the DOM that depend on changed signals are re-rendered, ensuring high performance even with complex charts.
