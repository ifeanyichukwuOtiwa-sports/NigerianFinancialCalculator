package iwo.wintech.ngnfincalc.scenarios.dto;

import java.math.BigDecimal;

public record CalculationParameters(
    BigDecimal principal,
    BigDecimal annualRate,
    Integer years,
    BigDecimal contribution,
    Integer periodsPerYear,
    String taxStrategy,
    BigDecimal annualIncome
) {}
