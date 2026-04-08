package iwo.wintech.ngnfincalc.scenarios.dto;

import java.math.BigDecimal;

public record ScenarioResponse(
    Long id,
    String brand,
    String name,
    BigDecimal principal,
    BigDecimal annualRate,
    Integer years,
    BigDecimal monthlyContribution,
    String compoundingFrequency,
    String taxStrategy,
    BigDecimal annualIncome,
    BigDecimal totalBalance,
    BigDecimal totalInterest,
    BigDecimal estimatedTax
) {}
