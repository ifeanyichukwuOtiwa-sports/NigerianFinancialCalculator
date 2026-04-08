package iwo.wintech.ngnfincalc.tax.repository;

import java.math.BigDecimal;

public record TaxBandDocument(
        BigDecimal upperLimit,
        BigDecimal rate
) {
}
