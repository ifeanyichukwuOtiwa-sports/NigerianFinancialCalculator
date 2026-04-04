/**
 * Supported compounding frequencies.
 */
export type CompoundingFrequency = 'monthly' | 'quarterly' | 'bi-annually' | 'annually';

/**
 * Supported income frequencies for tax calculation.
 */
export type IncomeFrequency = 'weekly' | 'monthly' | 'annually';

/**
 * Supported tax strategies for Nigeria Tax Act 2026.
 */
export type TaxStrategy = 'none' | 'wht' | 'progressive';

/**
 * Represents a single band in a progressive tax system.
 */
export interface TaxBand {
  limit: number;
  rate: number;
}

/**
 * UI Option for selecting a tax strategy.
 */
export interface TaxStrategyOption {
  label: string;
  value: TaxStrategy;
  description: string;
}

/**
 * Configuration option for a compounding frequency in the UI.
 */
export interface FrequencyOption {
  label: string;
  value: CompoundingFrequency;
  periods: number;
}

/**
 * Represents the comprehensive results of a compound interest and tax calculation.
 */
export interface CalculationResult {
  totalBalance: number;   // Gross
  totalInvested: number;
  totalInterest: number;  // Gross
  estimatedTax: number;
  netInterest: number;
  netBalance: number;     // Take-home
}

export interface TaxBandResult {
  band: TaxBand;
  taxableAmount: number;
  tax: number;
}

export interface PITResult {
  totalIncome: number;
  totalTax: number;
  netIncome: number;
  effectiveRate: number;
  bandResults: TaxBandResult[];
}

/**
 * Breakdown of financial data for a specific year.
 */
export interface YearBreakdown extends CalculationResult {
  year: number;
}

/**
 * Financial milestone reached at a certain point.
 */
export interface Milestone {
  year: number;
  label: string;
  value: number;
}
