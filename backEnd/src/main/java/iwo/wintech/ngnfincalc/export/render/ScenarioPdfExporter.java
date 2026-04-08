package iwo.wintech.ngnfincalc.export.render;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import iwo.wintech.ngnfincalc.scenarios.dto.ScenarioResponse;
import iwo.wintech.ngnfincalc.shared.error.ServerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ScenarioPdfExporter {

    private final TaxBreakdownPdfSectionWriter taxBreakdownPdfSectionWriter;
    private final TaxStrategyExplanationPdfSectionWriter taxStrategyExplanationPdfSectionWriter;

    public byte[] export(final ScenarioExportPayload payload) {
        final Document document = new Document();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, output);
            document.open();

            writeTitle(document, payload);
            writeScenarioName(document, payload.scenario());

            if (payload.taxPlan()) {
                writeTaxPlan(document, payload);
            } else {
                writeInvestmentScenario(document, payload.scenario());
            }
        } catch (final DocumentException exception) {
            throw new ServerException("Error generating PDF", exception);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }

        return output.toByteArray();
    }

    private void writeTitle(final Document document, final ScenarioExportPayload payload) throws DocumentException {
        final String titleText = payload.taxPlan() ? "Tax Plan Report" : "Investment Scenario Report";
        final Paragraph title = new Paragraph(titleText, ExportFormattingSupport.TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);
    }

    private void writeScenarioName(final Document document, final ScenarioResponse scenario) throws DocumentException {
        final Paragraph name = new Paragraph(
                "Scenario Name: " + scenario.name(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
        name.setSpacingAfter(10);
        document.add(name);
    }

    private void writeTaxPlan(final Document document, final ScenarioExportPayload payload) throws DocumentException {
        final PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingBefore(10);
        summaryTable.setSpacingAfter(20);

        ExportFormattingSupport.addTableRow(summaryTable, "Gross Annual Income",
                ExportFormattingSupport.formatCurrency(payload.scenario().annualIncome()));
        ExportFormattingSupport.addTableRow(summaryTable, "Total Annual Tax",
                ExportFormattingSupport.formatCurrency(payload.taxBreakdown().totalTax()));
        ExportFormattingSupport.addTableRow(summaryTable, "Effective Tax Rate", payload.taxBreakdown().effectiveRate() + "%");
        document.add(summaryTable);

        taxStrategyExplanationPdfSectionWriter.writeTaxReportExplanation(
                document,
                payload.scenario().annualIncome(),
                payload.taxBreakdown());
        taxBreakdownPdfSectionWriter.write(document, payload.taxBreakdown());
    }

    private void writeInvestmentScenario(final Document document, final ScenarioResponse scenario) throws DocumentException {
        final PdfPTable detailsTable = new PdfPTable(2);
        detailsTable.setWidthPercentage(100);
        detailsTable.setSpacingBefore(10);
        detailsTable.setSpacingAfter(10);

        ExportFormattingSupport.addTableRow(detailsTable, "Principal Amount",
                ExportFormattingSupport.formatCurrency(scenario.principal()));
        ExportFormattingSupport.addTableRow(detailsTable, "Annual Interest Rate", scenario.annualRate() + "%");
        ExportFormattingSupport.addTableRow(detailsTable, "Investment Period", scenario.years() + " years");
        ExportFormattingSupport.addTableRow(detailsTable, "Monthly Contribution",
                ExportFormattingSupport.formatCurrency(scenario.monthlyContribution()));
        ExportFormattingSupport.addTableRow(detailsTable, "Compounding Frequency", scenario.compoundingFrequency());
        ExportFormattingSupport.addTableRow(detailsTable, "Tax Strategy", scenario.taxStrategy());
        if (scenario.annualIncome() != null && scenario.annualIncome().compareTo(BigDecimal.ZERO) > 0) {
            ExportFormattingSupport.addTableRow(detailsTable, "Annual Income (for Tax)",
                    ExportFormattingSupport.formatCurrency(scenario.annualIncome()));
        }
        document.add(detailsTable);

        final Paragraph resultsHeader = new Paragraph("Financial Projections", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
        resultsHeader.setSpacingBefore(20);
        resultsHeader.setSpacingAfter(10);
        document.add(resultsHeader);

        final PdfPTable resultsTable = new PdfPTable(2);
        resultsTable.setWidthPercentage(100);
        ExportFormattingSupport.addResultRow(resultsTable, "Total Interest Earned",
                ExportFormattingSupport.formatCurrency(scenario.totalInterest()));
        ExportFormattingSupport.addResultRow(resultsTable, "Estimated Tax Liability",
                ExportFormattingSupport.formatCurrency(scenario.estimatedTax()));
        ExportFormattingSupport.addResultRow(resultsTable, "Final Projected Balance",
                ExportFormattingSupport.formatCurrency(scenario.totalBalance()));
        document.add(resultsTable);

        taxStrategyExplanationPdfSectionWriter.writeScenarioExplanation(
                document,
                scenario.taxStrategy(),
                scenario.annualIncome(),
                scenario.estimatedTax(),
                scenario.totalInterest(),
                null);
    }
}
