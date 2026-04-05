package iwo.wintech.ngnfincalc.repository;

import iwo.wintech.ngnfincalc.entity.InvestmentScenario;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InvestmentScenarioRepository {

    private final JdbcClient jdbcClient;

    private static final RowMapper<InvestmentScenario> SCENARIO_ROW_MAPPER = (rs, rowNum) -> InvestmentScenario.builder()
            .id(rs.getLong("id"))
            .brand(rs.getString("brand"))
            .userId(rs.getLong("user_id"))
            .name(rs.getString("name"))
            .principal(rs.getBigDecimal("principal"))
            .annualRate(rs.getBigDecimal("annual_rate"))
            .years(rs.getInt("years"))
            .monthlyContribution(rs.getBigDecimal("monthly_contribution"))
            .compoundingFrequency(rs.getString("compounding_frequency"))
            .taxStrategy(rs.getString("tax_strategy"))
            .annualIncome(rs.getBigDecimal("annual_income"))
            .totalBalance(rs.getBigDecimal("total_balance"))
            .totalInterest(rs.getBigDecimal("total_interest"))
            .estimatedTax(rs.getBigDecimal("estimated_tax"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .build();

    public List<InvestmentScenario> findAllByBrandAndUserId(String brand, Long userId) {
        return jdbcClient.sql("SELECT * FROM investment_scenarios WHERE brand = :brand AND user_id = :userId")
                .param("brand", brand)
                .param("userId", userId)
                .query(SCENARIO_ROW_MAPPER)
                .list();
    }

    public Optional<InvestmentScenario> findByBrandAndId(String brand, Long id) {
        return jdbcClient.sql("SELECT * FROM investment_scenarios WHERE brand = :brand AND id = :id")
                .param("brand", brand)
                .param("id", id)
                .query(SCENARIO_ROW_MAPPER)
                .optional();
    }

    public InvestmentScenario save(InvestmentScenario scenario) {
        if (scenario.id() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcClient.sql("""
                    INSERT INTO investment_scenarios (
                        brand, user_id, name, principal, annual_rate, years, monthly_contribution,
                        compounding_frequency, tax_strategy, annual_income, total_balance,
                        total_interest, estimated_tax
                    ) VALUES (
                        :brand, :userId, :name, :principal, :annualRate, :years, :monthlyContribution,
                        :compoundingFrequency, :taxStrategy, :annualIncome, :totalBalance,
                        :totalInterest, :estimatedTax
                    )
                    """)
                    .param("brand", scenario.brand())
                    .param("userId", scenario.userId())
                    .param("name", scenario.name())
                    .param("principal", scenario.principal())
                    .param("annualRate", scenario.annualRate())
                    .param("years", scenario.years())
                    .param("monthlyContribution", scenario.monthlyContribution())
                    .param("compoundingFrequency", scenario.compoundingFrequency())
                    .param("taxStrategy", scenario.taxStrategy())
                    .param("annualIncome", scenario.annualIncome())
                    .param("totalBalance", scenario.totalBalance())
                    .param("totalInterest", scenario.totalInterest())
                    .param("estimatedTax", scenario.estimatedTax())
                    .update(keyHolder);
            final Long id = Objects.requireNonNull(keyHolder.getKeyAs(BigInteger.class)).longValueExact();
            return scenario.toBuilder().id(id).build();
        } else {
            jdbcClient.sql("""
                    UPDATE investment_scenarios SET
                        brand = :brand, name = :name, principal = :principal, annual_rate = :annual_rate,
                        years = :years, monthly_contribution = :monthlyContribution,
                        compounding_frequency = :compoundingFrequency, tax_strategy = :taxStrategy,
                        annual_income = :annualIncome, total_balance = :totalBalance,
                        total_interest = :totalInterest, estimated_tax = :estimatedTax
                    WHERE id = :id
                    """)
                    .param("brand", scenario.brand())
                    .param("name", scenario.name())
                    .param("principal", scenario.principal())
                    .param("annual_rate", scenario.annualRate())
                    .param("years", scenario.years())
                    .param("monthlyContribution", scenario.monthlyContribution())
                    .param("compoundingFrequency", scenario.compoundingFrequency())
                    .param("taxStrategy", scenario.taxStrategy())
                    .param("annualIncome", scenario.annualIncome())
                    .param("totalBalance", scenario.totalBalance())
                    .param("totalInterest", scenario.totalInterest())
                    .param("estimatedTax", scenario.estimatedTax())
                    .param("id", scenario.id())
                    .update();
            return scenario;
        }
    }

    public void deleteByBrandAndId(String brand, Long id) {
        jdbcClient.sql("DELETE FROM investment_scenarios WHERE brand = :brand AND id = :id")
                .param("brand", brand)
                .param("id", id)
                .update();
    }
}
