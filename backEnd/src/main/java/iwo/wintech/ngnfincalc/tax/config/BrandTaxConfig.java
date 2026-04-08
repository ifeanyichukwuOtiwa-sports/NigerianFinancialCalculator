package iwo.wintech.ngnfincalc.tax.config;

import iwo.wintech.ngnfincalc.tax.service.TaxService;

import java.math.BigDecimal;
import java.util.List;

public record BrandTaxConfig(
        BigDecimal whtRate,
        List<TaxService.TaxBand> taxBands
) {
}
