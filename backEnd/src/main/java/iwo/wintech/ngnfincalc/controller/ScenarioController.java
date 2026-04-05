package iwo.wintech.ngnfincalc.controller;

import iwo.wintech.ngnfincalc.dto.ScenarioRequest;
import iwo.wintech.ngnfincalc.dto.ScenarioResponse;
import iwo.wintech.ngnfincalc.service.ScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

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
}
