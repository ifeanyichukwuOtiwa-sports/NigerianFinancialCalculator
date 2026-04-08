package iwo.wintech.ngnfincalc.export.service;

import iwo.wintech.ngnfincalc.export.render.ScenarioCsvExporter;
import iwo.wintech.ngnfincalc.export.render.ScenarioExportPayload;
import iwo.wintech.ngnfincalc.export.render.ScenarioExportPayloadFactory;
import iwo.wintech.ngnfincalc.export.render.ScenarioPdfExporter;
import iwo.wintech.ngnfincalc.export.render.TaxPdfExporter;
import iwo.wintech.ngnfincalc.scenarios.dto.ScenarioResponse;
import iwo.wintech.ngnfincalc.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final ScenarioExportPayloadFactory payloadFactory;
    private final ScenarioPdfExporter scenarioPdfExporter;
    private final ScenarioCsvExporter scenarioCsvExporter;
    private final TaxPdfExporter taxPdfExporter;

    public byte[] generateScenarioPdf(final ScenarioResponse scenario) {
        final ScenarioExportPayload payload = payloadFactory.create(scenario);
        return scenarioPdfExporter.export(payload);
    }

    public byte[] generateScenarioCsv(final ScenarioResponse scenario) {
        final ScenarioExportPayload payload = payloadFactory.create(scenario);
        return scenarioCsvExporter.export(payload);
    }

    public byte[] generateTaxPdf(final BigDecimal income, final TaxService.PITResult result) {
        return taxPdfExporter.export(income, result);
    }
}
