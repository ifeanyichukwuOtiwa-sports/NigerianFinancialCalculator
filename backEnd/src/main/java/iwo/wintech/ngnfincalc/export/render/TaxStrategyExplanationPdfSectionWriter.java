package iwo.wintech.ngnfincalc.export.render;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import iwo.wintech.ngnfincalc.shared.error.ServerException;
import iwo.wintech.ngnfincalc.tax.service.TaxService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TaxStrategyExplanationPdfSectionWriter {

    public void writeScenarioExplanation(
            final Document document,
            final String taxStrategy,
            final BigDecimal annualIncome,
            final BigDecimal estimatedTax,
            final BigDecimal totalInterest,
            final TaxService.PITResult taxBreakdown
    ) {
        try {
            addSectionTitle(document, "Tax Treatment Explanation");

            switch (normalizeStrategy(taxStrategy)) {
                case "wht" -> writeWhtExplanation(document, totalInterest, estimatedTax);
                case "progressive" -> writeProgressiveExplanation(document, annualIncome, totalInterest, taxBreakdown);
                default -> writeExemptExplanation(document);
            }
        } catch (final DocumentException exception) {
            throw new ServerException("Error generating tax explanation PDF section", exception);
        }
    }

    public void writeTaxReportExplanation(
            final Document document,
            final BigDecimal annualIncome,
            final TaxService.PITResult taxBreakdown
    ) {
        try {
            addSectionTitle(document, "How To Read This Report");
            addBody(document,
                    "This tax PDF uses the Progressive PIT framework. Tax is not a flat rate. "
                            + "Your annual income is split across tax bands, and each band is taxed at its own rate.");
            addBody(document,
                    "This means lower portions of income may be taxed at 0% or 15%, while higher portions can move into higher marginal bands.");
            addBody(document,
                    "For this report, the annual income input is the critical driver because it determines where taxation starts and how much falls into each band.");

            if (annualIncome != null && annualIncome.compareTo(BigDecimal.ZERO) > 0 && taxBreakdown != null) {
                addBody(document,
                        "The effective tax rate shown here is the blended outcome across all applicable bands, not the top marginal rate by itself.");
            }
        } catch (final DocumentException exception) {
            throw new ServerException("Error generating tax report explanation PDF section", exception);
        }
    }

    private void writeWhtExplanation(
            final Document document,
            final BigDecimal totalInterest,
            final BigDecimal estimatedTax
    ) throws DocumentException {
        addBody(document,
                "Withholding Tax (WHT) is a flat-rate tax collected at source before investment returns are fully received.");
        addBody(document,
                "In this calculator, WHT is applied at a fixed 10% rate to interest earned. "
                        + "That makes it simpler than the progressive tax model because only the investment return is taxed.");
        if (totalInterest != null && estimatedTax != null) {
            addBody(document,
                    "For this scenario, the estimated tax is based directly on the interest amount shown in the report. "
                            + "Annual income is not used when the WHT strategy is selected.");
        }
    }

    private void writeProgressiveExplanation(
            final Document document,
            final BigDecimal annualIncome,
            final BigDecimal totalInterest,
            final TaxService.PITResult taxBreakdown
    ) throws DocumentException {
        addBody(document,
                "Progressive PIT does not treat the investment in isolation. It measures the tax impact of adding investment returns to total annual income.");
        addBody(document,
                "The calculator adds investment interest to annual income, calculates tax on the combined amount, then subtracts the tax on annual income alone.");
        addBody(document,
                "This creates a marginal effect: lower-income users may see interest taxed in lower bands, while high-income earners may see additional interest taxed closer to the top marginal rates.");

        if (annualIncome != null && annualIncome.compareTo(BigDecimal.ZERO) > 0) {
            addBody(document,
                    "Because this strategy is band-driven, the Annual Income input is critical. "
                            + "It determines the band where the investment interest starts to be taxed.");
        } else {
            addBody(document,
                    "No annual income was provided, so the calculation effectively starts from the lowest tax bands.");
        }

        if (totalInterest != null && totalInterest.compareTo(BigDecimal.ZERO) > 0 && taxBreakdown != null) {
            addBody(document,
                    "The tax band breakdown below shows how the total taxable income is distributed across the PIT bands.");
        }
    }

    private void writeExemptExplanation(final Document document) throws DocumentException {
        addBody(document,
                "This scenario uses the exempt tax strategy, so no tax is applied to the projected investment interest.");
        addBody(document,
                "This is useful when modeling instruments or cases where the investment return is treated as tax-exempt within the assumptions of the calculator.");
    }

    private void addSectionTitle(final Document document, final String title) throws DocumentException {
        final Paragraph header = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
        header.setSpacingBefore(20);
        header.setSpacingAfter(8);
        document.add(header);
    }

    private void addBody(final Document document, final String text) throws DocumentException {
        final Paragraph paragraph = new Paragraph(text, ExportFormattingSupport.NORMAL_FONT);
        paragraph.setSpacingAfter(6);
        document.add(paragraph);
    }

    private String normalizeStrategy(final String taxStrategy) {
        return taxStrategy == null ? "none" : taxStrategy.trim().toLowerCase();
    }
}
