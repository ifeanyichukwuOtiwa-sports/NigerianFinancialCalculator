package iwo.wintech.ngnfincalc.tax.dto;

import java.math.BigDecimal;

public record TaxExportRequest(
        BigDecimal annualIncome
) {
}
