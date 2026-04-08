package iwo.wintech.ngnfincalc.scenarios.web;

import iwo.wintech.ngnfincalc.export.service.ExportService;
import iwo.wintech.ngnfincalc.scenarios.dto.ScenarioRequest;
import iwo.wintech.ngnfincalc.scenarios.dto.ScenarioResponse;
import iwo.wintech.ngnfincalc.scenarios.service.ScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;
    private final ExportService exportService;

    @PostMapping
    public ResponseEntity<ScenarioResponse> saveScenario(@Valid @RequestBody ScenarioRequest request) {
        return ResponseEntity.ok(scenarioService.saveScenario(request));
    }

    @GetMapping
    public ResponseEntity<List<ScenarioResponse>> getUserScenarios() {
        return ResponseEntity.ok(scenarioService.getUserScenarios());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScenario(@PathVariable Long id) {
        scenarioService.deleteScenario(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportToPdf(@PathVariable Long id) {
        final ScenarioResponse scenario = scenarioService.getScenario(id);
        final byte[] pdf = exportService.generateScenarioPdf(scenario);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scenario_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{id}/export/csv")
    public ResponseEntity<byte[]> exportToCsv(@PathVariable Long id) {
        final ScenarioResponse scenario = scenarioService.getScenario(id);
        final byte[] csv = exportService.generateScenarioCsv(scenario);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scenario_" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
