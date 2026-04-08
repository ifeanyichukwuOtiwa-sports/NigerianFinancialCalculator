package iwo.wintech.ngnfincalc.export.render;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import iwo.wintech.ngnfincalc.tax.service.TaxService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

final class ExportFormattingSupport {

    static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
    static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.WHITE);
    static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.of("en", "NG"));

    private ExportFormattingSupport() {
    }

    static void addTableRow(final PdfPTable table, final String label, final String value) {
        final PdfPCell cellLabel = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
        cellLabel.setPadding(5);
        cellLabel.setBackgroundColor(BaseColor.LIGHT_GRAY);
        table.addCell(cellLabel);

        final PdfPCell cellValue = new PdfPCell(new Phrase(value, NORMAL_FONT));
        cellValue.setPadding(5);
        table.addCell(cellValue);
    }

    static void addResultRow(final PdfPTable table, final String label, final String value) {
        final PdfPCell cellLabel = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
        cellLabel.setPadding(8);
        table.addCell(cellLabel);

        final PdfPCell cellValue =
                new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new BaseColor(0, 100, 0))));
        cellValue.setPadding(8);
        table.addCell(cellValue);
    }

    static void addBandHeaderCell(final PdfPTable table, final String text) {
        final PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(new BaseColor(30, 30, 80));
        cell.setPadding(7);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    static PdfPCell buildDataCell(final String text) {
        final PdfPCell cell = new PdfPCell(new Phrase(text, NORMAL_FONT));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    static String formatCurrency(final BigDecimal amount) {
        if (amount == null) {
            return CURRENCY_FORMAT.format(0);
        }
        return CURRENCY_FORMAT.format(amount);
    }

    static String escapeCsv(final String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    static String formatBandLabel(final TaxService.TaxBandResult bandResult) {
        return bandResult.band().limit() == null ? "Balance" : "Next ₦" + bandResult.band().limit().toPlainString();
    }

    static String formatRateLabel(final TaxService.TaxBandResult bandResult) {
        return bandResult.band().rate().multiply(new BigDecimal("100")).toPlainString() + "%";
    }
}
