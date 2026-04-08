package iwo.wintech.ngnfincalc.scenarios.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ScenarioRequest(
    String brand,

    @NotBlank(message = "Name is required")
    String name,

    @NotNull(message = "Principal is required")
    @DecimalMin(value = "0.0", message = "Principal must be positive")
    BigDecimal principal,

    @NotNull(message = "Annual rate is required")
    @DecimalMin(value = "0.0", message = "Annual rate must be positive")
    BigDecimal annualRate,

    @NotNull(message = "Years is required")
    @Min(value = 1, message = "Years must be at least 1")
    Integer years,

    @NotNull(message = "Monthly contribution is required")
    @DecimalMin(value = "0.0", message = "Monthly contribution must be positive")
    BigDecimal monthlyContribution,

    @NotBlank(message = "Compounding frequency is required")
    String compoundingFrequency,

    @NotBlank(message = "Tax strategy is required")
    String taxStrategy,

    @DecimalMin(value = "0.0", message = "Annual income must be positive")
    BigDecimal annualIncome
) {}
