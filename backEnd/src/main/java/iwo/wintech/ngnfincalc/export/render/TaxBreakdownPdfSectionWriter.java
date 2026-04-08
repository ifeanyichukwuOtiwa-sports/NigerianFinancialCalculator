package iwo.wintech.ngnfincalc.export.render;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import iwo.wintech.ngnfincalc.shared.error.ServerException;
import iwo.wintech.ngnfincalc.tax.service.TaxService;
import org.springframework.stereotype.Component;

@Component
public class TaxBreakdownPdfSectionWriter {

    public void write(final Document document, final TaxService.PITResult result) {
        try {
            final Paragraph breakdownHeader = new Paragraph(
                    "Tax Band Breakdown",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
            breakdownHeader.setSpacingAfter(10);
            document.add(breakdownHeader);

            final PdfPTable bandTable = new PdfPTable(4);
            bandTable.setWidthPercentage(100);
            bandTable.setWidths(new float[]{3f, 1.5f, 3f, 3f});
            bandTable.setSpacingBefore(5);
            bandTable.setSpacingAfter(10);

            ExportFormattingSupport.addBandHeaderCell(bandTable, "Band");
            ExportFormattingSupport.addBandHeaderCell(bandTable, "Rate");
            ExportFormattingSupport.addBandHeaderCell(bandTable, "Taxable Amount");
            ExportFormattingSupport.addBandHeaderCell(bandTable, "Tax");

            for (final TaxService.TaxBandResult bandResult : result.bandResults()) {
                bandTable.addCell(ExportFormattingSupport.buildDataCell(ExportFormattingSupport.formatBandLabel(bandResult)));
                bandTable.addCell(ExportFormattingSupport.buildDataCell(ExportFormattingSupport.formatRateLabel(bandResult)));
                bandTable.addCell(
                        ExportFormattingSupport.buildDataCell(
                                ExportFormattingSupport.formatCurrency(bandResult.taxableAmount())));
                bandTable.addCell(
                        ExportFormattingSupport.buildDataCell(
                                ExportFormattingSupport.formatCurrency(bandResult.tax())));
            }

            document.add(bandTable);
        } catch (final DocumentException exception) {
            throw new ServerException("Error generating tax breakdown PDF section", exception);
        }
    }
}
