# Nigerian Financial Calculator

A modern Angular 21 frontend for the Nigerian Financial Calculator. It provides investment projection, tax estimation, authentication, and saved-scenario workflows for the backend API in this repository.

![Project Overview](ci.png)

## 🌟 Key Features

### 1. Compound Interest Calculator
*   **Visualize Growth**: Real-time interactive bar charts showing the breakdown of principal vs. interest over time.
*   **Flexible Compounding**: Support for Monthly, Quarterly, Bi-Annual, and Annual compounding frequencies.
*   **Click-to-Edit Inputs**: Seamlessly toggle between sliders and direct numeric entry for precise control.
*   **Milestone Tracking**: Automatic detection of wealth doubling/tripling points (e.g., "2x Principal reached in Year 8").
*   **Yearly Breakdown**: Click any bar to see specific invested, interest, and total balance details for that year.
*   **Large Range Support**: Plan for the future with initial investments up to ₦1,000,000.

### 2. Nigeria Tax Calculator (2026 Rules)
*   **Configured Tax Bands**: Uses the repository's current 2026 Nigeria tax-band configuration.
*   **Progressive PIT**: Calculates Personal Income Tax based on the updated step-function bands (₦800k exempt, then 15%, 18%, etc.).
*   **Two-Way Calculation**:
    *   **Income to Tax**: Enter your gross wage to see your net take-home and estimated tax.
    *   **Tax to Income**: Enter a target net tax amount to extrapolate the required gross income.
*   **Multi-Frequency Support**: Input and view results in Weekly (up to ₦1M), Monthly (up to ₦100M), or Annual (up to ₦1B) formats.
*   **Tax-Free Threshold**: Accurate extrapolation that accounts for the ₦800,000 annual tax-free allowance.

### 3. Dynamic Theme System
*   **3-Way Theme Selector**: Manual [☀️ Day], [🌙 Night], or [🖥️ System] modes to suit your preference.
*   **Time-Based Intensity**: Accent colors automatically shift throughout the day (Midnight Cyan, Morning Amber, Noon Violet, Evening Emerald).
*   **Glassmorphism UI**: Modern aesthetic with frosted glass effects and smooth transitions.

## 🛠️ Technical Stack

*   **Framework**: [Angular v21.2.6](https://angular.dev/) (Standalone Components, Signals API).
*   **State Management**: Fully reactive using **Angular Signals**.
*   **Visualization**: [Chart.js](https://www.chartjs.org/) for high-performance interactive graphing.
*   **Styling**: SCSS with CSS variable tokens for unified theme management.
*   **Build Tool**: Angular CLI / Vite.
*   **Testing**: `ng test` via Angular's unit-test builder.

## 🏗️ Architecture & Design

The application follows a modern, modular architecture organized by feature and responsibility, optimized for scalability and performance:

### 📁 Directory Structure
*   **`src/app/core/`**: Singleton services (`Auth`, `Navigation`, `Theme`), global interceptors, and route guards. This is the "brain" of the app.
*   **`src/app/shared/`**: Reusable UI components, pipes, constants, and pure utility functions used across multiple features.
*   **`src/app/features/`**: Logical modules organized by business domain. Each feature encapsulates its own components, local services, and routing:
    *   `investment/`: Compound interest calculator and interactive charts.
    *   `tax/`: Nigeria 2026 Personal Income Tax tools.
    *   `scenarios/`: User-saved scenario management and exports.
    *   `auth/`: Login and registration flows.
*   **`src/app/layout/`**: Global layout components like the `Navbar`.

### 🚀 Key Patterns
*   **Route-Based Lazy Loading**: Each feature in the `features/` directory is lazy-loaded via the Angular Router, ensuring users only download the code they need for the current view.
*   **Signal-First State**: Fully reactive state management using Angular Signals (`signal`, `computed`, `effect`) for predictable data flow.
*   **Pure Functional Logic**: All mathematical formulas (financial and tax) are isolated in `shared/utils/` for absolute portability and ease of testing.
*   **OnPush Strategy**: Optimized change detection across all components for maximum performance.

## 🚀 Getting Started

### Prerequisites
*   Node.js 24 LTS
*   npm

### Installation
1. Clone the repository.
2. Install dependencies:
   ```bash
   nvm use
   npm install
   ```

### Development Server
Run the local development server:
```bash
npm start
```
Navigate to `http://localhost:4200/`. The app will automatically reload on source changes.

For API-backed development, start the backend separately from the repository root docs or [`backEnd/README.md`](../backEnd/README.md). The dev server proxies `/api` requests to `http://localhost:8080`.

### Building for Production
To build the project:
```bash
npm run build
```
The build artifacts will be stored in the `dist/` directory.

### Linting
```bash
npm run lint
```

### Tests
```bash
npm test
```

## 📊 Business Rules (Nigeria Tax Act 2026)

The app currently implements the following progressive tax bands from its checked-in configuration:

| Annual Income Band | Tax Rate |
|-------------------|----------|
| First ₦800,000    | 0% (Exempt) |
| Next ₦2,200,000   | 15% |
| Next ₦9,000,000   | 18% |
| Next ₦13,000,000  | 21% |
| Next ₦25,000,000  | 23% |
| Above ₦50,000,000 | 25% |

---
*Created with focus on human readability and ease of maintenance.*
