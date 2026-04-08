package iwo.wintech.ngnfincalc.tax.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaxService {

    public record TaxBand(BigDecimal limit, BigDecimal rate) {}

    public record TaxBandResult(TaxBand band, BigDecimal taxableAmount, BigDecimal tax) {}

    public record PITResult(
        BigDecimal totalIncome,
        BigDecimal totalTax,
        BigDecimal netIncome,
        BigDecimal effectiveRate,
        List<TaxBandResult> bandResults
    ) {}

    public PITResult calculateDetailedPIT(BigDecimal income, List<TaxBand> bands) {
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            return new PITResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 
                bands.stream().map(b -> new TaxBandResult(b, BigDecimal.ZERO, BigDecimal.ZERO)).toList());
        }

        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal remaining = income;
        List<TaxBandResult> bandResults = new ArrayList<>();

        for (TaxBand band : bands) {
            BigDecimal taxableAmount;
            if (band.limit() == null) {
                taxableAmount = remaining;
            } else {
                taxableAmount = remaining.min(band.limit());
            }

            BigDecimal tax = taxableAmount.multiply(band.rate());
            totalTax = totalTax.add(tax);
            remaining = remaining.subtract(taxableAmount);
            bandResults.add(new TaxBandResult(band, taxableAmount, tax));

            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                // Fill rest of bands with zeros to match frontend behavior
                int currentSize = bandResults.size();
                for (int i = currentSize; i < bands.size(); i++) {
                    bandResults.add(new TaxBandResult(bands.get(i), BigDecimal.ZERO, BigDecimal.ZERO));
                }
                break;
            }
        }

        BigDecimal effectiveRate = totalTax.multiply(new BigDecimal("100"))
                .divide(income, 4, RoundingMode.HALF_UP);

        return new PITResult(
            income,
            totalTax,
            income.subtract(totalTax),
            effectiveRate,
            bandResults
        );
    }
}
