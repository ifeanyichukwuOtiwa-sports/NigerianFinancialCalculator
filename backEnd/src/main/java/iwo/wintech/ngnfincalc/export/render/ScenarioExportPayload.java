package iwo.wintech.ngnfincalc.export.render;

import iwo.wintech.ngnfincalc.scenarios.dto.ScenarioResponse;
import iwo.wintech.ngnfincalc.tax.service.TaxService;

public record ScenarioExportPayload(
        ScenarioResponse scenario,
        boolean taxPlan,
        TaxService.PITResult taxBreakdown
) {
}
