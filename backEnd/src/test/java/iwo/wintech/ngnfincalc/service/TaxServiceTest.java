package iwo.wintech.ngnfincalc.service;

import iwo.wintech.ngnfincalc.tax.service.TaxService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaxServiceTest {

    private final TaxService taxService = new TaxService();

    private static final List<TaxService.TaxBand> NIGERIA_PIT_BANDS_2026 = List.of(
        new TaxService.TaxBand(new BigDecimal("800000"), BigDecimal.ZERO),
        new TaxService.TaxBand(new BigDecimal("2200000"), new BigDecimal("0.15")),
        new TaxService.TaxBand(new BigDecimal("9000000"), new BigDecimal("0.18")),
        new TaxService.TaxBand(new BigDecimal("13000000"), new BigDecimal("0.21")),
        new TaxService.TaxBand(new BigDecimal("25000000"), new BigDecimal("0.23")),
        new TaxService.TaxBand(null, new BigDecimal("0.25"))
    );

    @Test
    void testCalculateDetailedPIT_ZeroIncome() {
        TaxService.PITResult result = taxService.calculateDetailedPIT(BigDecimal.ZERO, NIGERIA_PIT_BANDS_2026);
        assertThat(BigDecimal.ZERO.compareTo(result.totalTax())).isZero();
    }

    @Test
    void testCalculateDetailedPIT_BelowExempt() {
        TaxService.PITResult result = taxService.calculateDetailedPIT(new BigDecimal("500000"), NIGERIA_PIT_BANDS_2026);
        assertThat(BigDecimal.ZERO.compareTo(result.totalTax())).isZero();
    }

    @Test
    void testCalculateDetailedPIT_FirstProgressiveBand() {
        // First 800k exempt. Next 2.2M at 15%.
        // Income 1M -> 800k exempt, 200k at 15% = 30k
        TaxService.PITResult result = taxService.calculateDetailedPIT(new BigDecimal("1000000"), NIGERIA_PIT_BANDS_2026);
        assertThat(new BigDecimal("30000.00").compareTo(result.totalTax())).isZero();
    }

    @Test
    void testDetailedPIT_BandResultsCount() {
        TaxService.PITResult result = taxService.calculateDetailedPIT(new BigDecimal("1000000"), NIGERIA_PIT_BANDS_2026);
        assertThat(result.bandResults()).hasSize(NIGERIA_PIT_BANDS_2026.size());
    }
}
