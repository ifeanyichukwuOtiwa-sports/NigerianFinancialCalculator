package iwo.wintech.ngnfincalc.tax.repository;

import iwo.wintech.ngnfincalc.shared.error.ServerException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TaxBandRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public BrandTaxConfigDocument findConfigByBrand(final String brand) {
        return jdbcClient.sql("SELECT config FROM brand_configs WHERE brand = :brand")
                .param("brand", brand)
                .query((rs, rowNum) -> {
                    final String json = rs.getString("config");
                    try {
                        return objectMapper.readValue(json, BrandTaxConfigDocument.class);
                    } catch (final JacksonException e) {
                        throw new ServerException("Failed to parse tax config for brand " + brand, e);
                    }
                })
                .optional()
                .orElse(new BrandTaxConfigDocument(BigDecimal.ZERO, List.of()));
    }
}
