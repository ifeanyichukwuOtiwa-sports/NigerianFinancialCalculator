package iwo.wintech.ngnfincalc.scenarios.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder(toBuilder = true)
public record InvestmentScenario(
    Long id,
    String brand,
    Long userId,
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
    BigDecimal estimatedTax,
    LocalDateTime createdAt
) {
}
