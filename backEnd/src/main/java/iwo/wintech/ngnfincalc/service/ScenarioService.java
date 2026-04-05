package iwo.wintech.ngnfincalc.service;

import iwo.wintech.ngnfincalc.config.TenantContext;
import iwo.wintech.ngnfincalc.dto.PeriodDto;
import iwo.wintech.ngnfincalc.exception.ErrorCode;
import iwo.wintech.ngnfincalc.exception.RequestException;
import iwo.wintech.ngnfincalc.entity.InvestmentScenario;
import iwo.wintech.ngnfincalc.dto.CalculationParameters;
import iwo.wintech.ngnfincalc.dto.CalculationResult;
import iwo.wintech.ngnfincalc.dto.ScenarioRequest;
import iwo.wintech.ngnfincalc.dto.ScenarioResponse;
import iwo.wintech.ngnfincalc.repository.InvestmentScenarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScenarioService {

    private final InvestmentScenarioRepository scenarioRepository;
    private final CalculationService calculationService;
    private final TenantContext tenantContext;

    @Transactional
    public ScenarioResponse saveScenario(final ScenarioRequest request) {
        final TenantContext.TenantInfo tenant = tenantContext.getTenantInfo();

        final Integer periodsPerYear = getPeriodsPerYear(request.compoundingFrequency());
        final CalculationParameters calcParams = new CalculationParameters(
                request.principal(),
                request.annualRate(),
                request.years(),
                request.monthlyContribution(),
                periodsPerYear,
                request.taxStrategy(),
                request.annualIncome()
        );
        final CalculationResult calc = calculationService.calculateFutureBalance(calcParams);

        final InvestmentScenario scenario = InvestmentScenario.builder()
                .brand(tenant.brand())
                .userId(tenant.userId())
                .name(request.name())
                .principal(request.principal())
                .annualRate(request.annualRate())
                .years(request.years())
                .monthlyContribution(request.monthlyContribution())
                .compoundingFrequency(request.compoundingFrequency())
                .taxStrategy(request.taxStrategy())
                .annualIncome(request.annualIncome())
                .totalBalance(calc.totalBalance())
                .totalInterest(calc.totalInterest())
                .estimatedTax(calc.estimatedTax())
                .build();

        final InvestmentScenario saved = scenarioRepository.save(scenario);
        return mapToResponse(saved);
    }

    public List<ScenarioResponse> getUserScenarios() {
        final TenantContext.TenantInfo tenant = tenantContext.getTenantInfo();

        return scenarioRepository.findAllByBrandAndUserId(tenant.brand(), tenant.userId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void deleteScenario(final Long id) {
        final TenantContext.TenantInfo tenant = tenantContext.getTenantInfo();

        scenarioRepository.findByBrandAndId(tenant.brand(), id)
                .orElseThrow(() -> new RequestException("Scenario not found or access denied",
                        ErrorCode.INVALID_INPUT, Map.of("scenarioId", id)));

        scenarioRepository.deleteByBrandAndId(tenant.brand(), id);
    }

    private Integer getPeriodsPerYear(final String frequency) {
        if (frequency == null) {
            return PeriodDto.DEFAULT.getValue();
        }
        return switch (frequency.toLowerCase()) {
            case "monthly" -> PeriodDto.MONTHLY.getValue();
            case "quarterly" -> PeriodDto.QUARTERLY.getValue();
            case "bi-annually" -> PeriodDto.BI_ANNUALLY.getValue();
            case "annually" -> PeriodDto.ANNUALLY.getValue();
            default -> PeriodDto.DEFAULT.getValue();
        };
    }

    private ScenarioResponse mapToResponse(final InvestmentScenario s) {
        return new ScenarioResponse(
            s.id(),
            s.brand(),
            s.name(),
            s.principal(),
            s.annualRate(),
            s.years(),
            s.monthlyContribution(),
            s.compoundingFrequency(),
            s.taxStrategy(),
            s.annualIncome(),
            s.totalBalance(),
            s.totalInterest(),
            s.estimatedTax()
        );
    }
}
