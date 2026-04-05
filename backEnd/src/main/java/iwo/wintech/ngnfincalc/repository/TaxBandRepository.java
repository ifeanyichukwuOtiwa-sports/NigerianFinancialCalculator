package iwo.wintech.ngnfincalc.repository;

import iwo.wintech.ngnfincalc.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TaxBandRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public record BrandTaxConfig(List<TaxService.TaxBand> taxBands, BigDecimal whtRate) {}

    public BrandTaxConfig findConfigByBrand(String brand) {
        return jdbcClient.sql("SELECT config FROM brand_configs WHERE brand = :brand")
                .param("brand", brand)
                .query((rs, rowNum) -> {
                    String json = rs.getString("config");
                    try {
                        final JsonNode root = objectMapper.readTree(json);
                        
                        // Parse WHT Rate
                        BigDecimal whtRate = root.hasNonNull("whtRate") 
                                ? root.get("whtRate").decimalValue() 
                                : BigDecimal.ZERO;

                        // Parse Tax Bands
                        final JsonNode bandsNode = root.path("taxBands");
                        final List<TaxService.TaxBand> bands = new ArrayList<>();
                        if (bandsNode.isArray()) {
                            for (JsonNode node : bandsNode) {
                                bands.add(new TaxService.TaxBand(
                                        node.hasNonNull("upperLimit") ? node.get("upperLimit").decimalValue() : null,
                                        node.get("rate").decimalValue()
                                ));
                            }
                        }
                        return new BrandTaxConfig(bands, whtRate);
                    } catch (final JacksonException e) {
                        throw new RuntimeException("Failed to parse tax bands JSON", e);
                    }
                })
                .optional()
                .orElse(new BrandTaxConfig(List.of(), BigDecimal.ZERO));
    }
}
