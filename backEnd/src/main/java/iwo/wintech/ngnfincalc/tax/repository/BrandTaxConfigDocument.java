package iwo.wintech.ngnfincalc.tax.repository;

import java.math.BigDecimal;
import java.util.List;

public record BrandTaxConfigDocument(
        BigDecimal whtRate,
        List<TaxBandDocument> taxBands
) {
}
