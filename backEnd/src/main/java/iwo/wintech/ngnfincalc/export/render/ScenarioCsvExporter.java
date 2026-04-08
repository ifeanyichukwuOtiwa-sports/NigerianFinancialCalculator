package iwo.wintech.ngnfincalc.export.render;

import org.springframework.stereotype.Component;

@Component
public class ScenarioCsvExporter {

    public byte[] export(final ScenarioExportPayload payload) {
        final StringBuilder csv = new StringBuilder("Field,Value\n");
        csv.append("Scenario Name,").append(ExportFormattingSupport.escapeCsv(payload.scenario().name())).append("\n");

        if (payload.taxPlan()) {
            appendTaxPlanRows(csv, payload);
        } else {
            appendInvestmentRows(csv, payload);
        }

        return csv.toString().getBytes();
    }

    private void appendTaxPlanRows(final StringBuilder csv, final ScenarioExportPayload payload) {
        csv.append("Gross Annual Income,").append(payload.scenario().annualIncome()).append("\n");
        csv.append("Total Annual Tax,").append(payload.taxBreakdown().totalTax()).append("\n");
        csv.append("Net Annual Income,").append(payload.taxBreakdown().netIncome()).append("\n");
        csv.append("Effective Tax Rate,").append(payload.taxBreakdown().effectiveRate()).append("%\n\n");

        csv.append("Tax Band,Rate,Taxable Amount,Tax\n");
        for (final var bandResult : payload.taxBreakdown().bandResults()) {
            csv.append(ExportFormattingSupport.escapeCsv(ExportFormattingSupport.formatBandLabel(bandResult))).append(",")
                    .append(ExportFormattingSupport.formatRateLabel(bandResult)).append(",")
                    .append(bandResult.taxableAmount()).append(",")
                    .append(bandResult.tax()).append("\n");
        }
    }

    private void appendInvestmentRows(final StringBuilder csv, final ScenarioExportPayload payload) {
        final var scenario = payload.scenario();
        csv.append("Principal Amount,").append(scenario.principal()).append("\n");
        csv.append("Annual Interest Rate,").append(scenario.annualRate()).append("\n");
        csv.append("Investment Period (Years),").append(scenario.years()).append("\n");
        csv.append("Monthly Contribution,").append(scenario.monthlyContribution()).append("\n");
        csv.append("Compounding Frequency,").append(scenario.compoundingFrequency()).append("\n");
        csv.append("Tax Strategy,").append(scenario.taxStrategy()).append("\n");
        csv.append("Annual Income,").append(scenario.annualIncome()).append("\n");
        csv.append("Total Interest Earned,").append(scenario.totalInterest()).append("\n");
        csv.append("Estimated Tax Liability,").append(scenario.estimatedTax()).append("\n");
        csv.append("Final Projected Balance,").append(scenario.totalBalance()).append("\n");
    }
}
