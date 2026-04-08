package iwo.wintech.ngnfincalc.scenarios.service;

import iwo.wintech.ngnfincalc.platform.tenancy.BrandContext;
import iwo.wintech.ngnfincalc.scenarios.dto.CalculationParameters;
import iwo.wintech.ngnfincalc.scenarios.dto.CalculationResult;
import iwo.wintech.ngnfincalc.tax.config.BrandTaxConfig;
import iwo.wintech.ngnfincalc.tax.config.BrandTaxConfigMapper;
import iwo.wintech.ngnfincalc.tax.repository.TaxBandRepository;
import iwo.wintech.ngnfincalc.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.math.MathContext;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalculationService {
    private static final MathContext MATH_CONTEXT = new MathContext(64, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final TaxService taxService;
    private final TaxBandRepository taxBandRepository;
    private final BrandTaxConfigMapper brandTaxConfigMapper;

    public CalculationResult calculateFutureBalance(final CalculationParameters params) {
        final int periodsPerYear = params.periodsPerYear() != null ? params.periodsPerYear() : 12;
        final BigDecimal nt = new BigDecimal(periodsPerYear).multiply(new BigDecimal(params.years()));

        // 1. Calculate Core Financials
        final BigDecimal totalBalance = calculateTotalBalance(params, periodsPerYear, nt);
        final BigDecimal totalInvested = calculateTotalInvested(params, nt);
        final BigDecimal totalInterest = totalBalance.subtract(totalInvested).setScale(4, RoundingMode.HALF_UP);

        // 2. Calculate Tax
        final BigDecimal estimatedTax = calculateEstimatedTax(params, totalInterest);

        // 3. Final Result
        return new CalculationResult(
            totalBalance.setScale(4, RoundingMode.HALF_UP),
            totalInvested.setScale(4, RoundingMode.HALF_UP),
            totalInterest,
            estimatedTax,
            totalInterest.subtract(estimatedTax).setScale(4, RoundingMode.HALF_UP),
            totalBalance.subtract(estimatedTax).setScale(4, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal calculateTotalBalance(final CalculationParameters params, final int periodsPerYear, final BigDecimal nt) {
        final BigDecimal periodicRate = params.annualRate()
                .divide(HUNDRED, MATH_CONTEXT)
                .divide(new BigDecimal(periodsPerYear), MATH_CONTEXT);

        final BigDecimal onePlusRPowNT = BigDecimal.ONE.add(periodicRate).pow(nt.intValue(), MATH_CONTEXT);

        final BigDecimal compoundPrincipal = params.principal().multiply(onePlusRPowNT, MATH_CONTEXT);
        final BigDecimal futureValueAnnuity = calculateAnnuity(params.contribution(), periodicRate, onePlusRPowNT, nt);

        return compoundPrincipal.add(futureValueAnnuity);
    }

    private BigDecimal calculateAnnuity(final BigDecimal contribution, final BigDecimal periodicRate, final BigDecimal onePlusRPowNT, final BigDecimal nt) {
        if (periodicRate.compareTo(BigDecimal.ZERO) == 0) {
            return contribution.multiply(nt, MATH_CONTEXT);
        }
        return contribution.multiply(
            onePlusRPowNT.subtract(BigDecimal.ONE).divide(periodicRate, MATH_CONTEXT),
            MATH_CONTEXT
        );
    }

    private BigDecimal calculateTotalInvested(final CalculationParameters params, final BigDecimal nt) {
        return params.principal().add(params.contribution().multiply(nt, MATH_CONTEXT));
    }

    private BigDecimal calculateEstimatedTax(final CalculationParameters params, final BigDecimal totalInterest) {
        if ("none".equalsIgnoreCase(params.taxStrategy())) {
            return BigDecimal.ZERO;
        }

        final BrandTaxConfig brandConfig = brandTaxConfigMapper.toDomain(
                taxBandRepository.findConfigByBrand(BrandContext.get()));
        
        return switch (params.taxStrategy().toLowerCase()) {
            case "wht" -> totalInterest.multiply(brandConfig.whtRate(), MATH_CONTEXT).setScale(4, RoundingMode.HALF_UP);
            case "progressive" -> calculateProgressiveTax(params, totalInterest, brandConfig.taxBands());
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal calculateProgressiveTax(final CalculationParameters params, final BigDecimal totalInterest, final List<TaxService.TaxBand> bands) {
        final BigDecimal annualIncome = params.annualIncome() != null ? params.annualIncome() : BigDecimal.ZERO;
        final BigDecimal taxWithInterest = taxService.calculateDetailedPIT(annualIncome.add(totalInterest), bands).totalTax();
        final BigDecimal taxBase = taxService.calculateDetailedPIT(annualIncome, bands).totalTax();
        return taxWithInterest.subtract(taxBase).setScale(4, RoundingMode.HALF_UP);
    }
}
