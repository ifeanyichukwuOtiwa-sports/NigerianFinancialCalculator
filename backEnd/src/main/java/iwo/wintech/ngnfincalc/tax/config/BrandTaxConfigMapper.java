package iwo.wintech.ngnfincalc.tax.config;

import iwo.wintech.ngnfincalc.tax.repository.BrandTaxConfigDocument;
import iwo.wintech.ngnfincalc.tax.repository.TaxBandDocument;
import iwo.wintech.ngnfincalc.tax.service.TaxService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class BrandTaxConfigMapper {

    public BrandTaxConfig toDomain(final BrandTaxConfigDocument document) {
        if (document == null) {
            return empty();
        }

        final BigDecimal whtRate = document.whtRate() == null ? BigDecimal.ZERO : document.whtRate();
        final List<TaxService.TaxBand> taxBands = document.taxBands() == null
                ? List.of()
                : document.taxBands().stream()
                .map(this::toTaxBand)
                .toList();

        return new BrandTaxConfig(whtRate, taxBands);
    }

    public BrandTaxConfig empty() {
        return new BrandTaxConfig(BigDecimal.ZERO, List.of());
    }

    private TaxService.TaxBand toTaxBand(final TaxBandDocument document) {
        return new TaxService.TaxBand(document.upperLimit(), document.rate());
    }
}
