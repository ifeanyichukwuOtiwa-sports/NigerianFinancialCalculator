package iwo.wintech.ngnfincalc.tax.web;

import iwo.wintech.ngnfincalc.export.service.ExportService;
import iwo.wintech.ngnfincalc.platform.tenancy.BrandContext;
import iwo.wintech.ngnfincalc.tax.config.BrandTaxConfigMapper;
import iwo.wintech.ngnfincalc.tax.dto.TaxExportRequest;
import iwo.wintech.ngnfincalc.tax.repository.TaxBandRepository;
import iwo.wintech.ngnfincalc.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/tax")
@RequiredArgsConstructor
@RestController
public class TaxController {
    private final TaxService taxService;
    private final ExportService exportService;
    private final TaxBandRepository taxBandRepository;
    private final BrandTaxConfigMapper brandTaxConfigMapper;

    @PostMapping("/export/pdf")
    public ResponseEntity<byte[]> exportTaxPdf(@RequestBody TaxExportRequest request) {
        var config = brandTaxConfigMapper.toDomain(taxBandRepository.findConfigByBrand(BrandContext.get()));
        var result = taxService.calculateDetailedPIT(request.annualIncome(), config.taxBands());
        byte[] pdf = exportService.generateTaxPdf(request.annualIncome(), result);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tax_report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
