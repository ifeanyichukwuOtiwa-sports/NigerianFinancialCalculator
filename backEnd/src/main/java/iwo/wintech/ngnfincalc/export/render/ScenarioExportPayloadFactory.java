package iwo.wintech.ngnfincalc.export.render;

import iwo.wintech.ngnfincalc.scenarios.dto.ScenarioResponse;
import iwo.wintech.ngnfincalc.tax.config.BrandTaxConfig;
import iwo.wintech.ngnfincalc.tax.config.BrandTaxConfigMapper;
import iwo.wintech.ngnfincalc.tax.repository.TaxBandRepository;
import iwo.wintech.ngnfincalc.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ScenarioExportPayloadFactory {

    private final TaxService taxService;
    private final TaxBandRepository taxBandRepository;
    private final BrandTaxConfigMapper brandTaxConfigMapper;

    public ScenarioExportPayload create(final ScenarioResponse scenario) {
        if (!isTaxPlan(scenario)) {
            return new ScenarioExportPayload(scenario, false, null);
        }

        final BrandTaxConfig config = brandTaxConfigMapper.toDomain(taxBandRepository.findConfigByBrand(scenario.brand()));
        final TaxService.PITResult taxBreakdown = taxService.calculateDetailedPIT(scenario.annualIncome(), config.taxBands());
        return new ScenarioExportPayload(scenario, true, taxBreakdown);
    }

    private boolean isTaxPlan(final ScenarioResponse scenario) {
        return scenario.principal().compareTo(BigDecimal.ZERO) == 0
                && scenario.annualRate().compareTo(BigDecimal.ZERO) == 0;
    }
}
