package iwo.wintech.ngnfincalc.dto;

import java.math.BigDecimal;

public record CalculationResult(
        BigDecimal totalBalance,
        BigDecimal totalInvested,
        BigDecimal totalInterest,
        BigDecimal estimatedTax,
        BigDecimal netInterest,
        BigDecimal netBalance
) {
}
