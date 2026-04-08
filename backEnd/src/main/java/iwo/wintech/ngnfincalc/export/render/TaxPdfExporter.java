package iwo.wintech.ngnfincalc.export.render;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import iwo.wintech.ngnfincalc.shared.error.ServerException;
import iwo.wintech.ngnfincalc.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class TaxPdfExporter {

    private final TaxBreakdownPdfSectionWriter taxBreakdownPdfSectionWriter;
    private final TaxStrategyExplanationPdfSectionWriter taxStrategyExplanationPdfSectionWriter;

    public byte[] export(final BigDecimal income, final TaxService.PITResult result) {
        final Document document = new Document();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, output);
            document.open();

            final Paragraph title = new Paragraph("Personal Income Tax Report (Nigeria 2026)", ExportFormattingSupport.TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            final PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingBefore(10);
            summaryTable.setSpacingAfter(20);
            ExportFormattingSupport.addTableRow(summaryTable, "Gross Annual Income", ExportFormattingSupport.formatCurrency(income));
            ExportFormattingSupport.addTableRow(summaryTable, "Total Annual Tax", ExportFormattingSupport.formatCurrency(result.totalTax()));
            ExportFormattingSupport.addTableRow(summaryTable, "Effective Tax Rate", result.effectiveRate() + "%");
            document.add(summaryTable);

            taxStrategyExplanationPdfSectionWriter.writeTaxReportExplanation(document, income, result);
            taxBreakdownPdfSectionWriter.write(document, result);
        } catch (final DocumentException exception) {
            throw new ServerException("Error generating Tax PDF", exception);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }

        return output.toByteArray();
    }
}
