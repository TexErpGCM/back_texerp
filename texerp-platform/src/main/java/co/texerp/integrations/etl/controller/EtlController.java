
package co.texerp.integrations.etl.controller;

import co.texerp.integrations.etl.service.EtlExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/etl")
public class EtlController {

    private final EtlExecutionService executionService;

    public EtlController(EtlExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/jobs/{jobName}/execute")
    @PreAuthorize("hasAuthority('ETL_EXECUTE')")
    public ResponseEntity<Map<String, Object>> execute(
            @PathVariable String jobName
    ) {
        Long executionId = executionService.requestExecution(jobName);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "executionId", executionId,
                        "status", "PENDING"
                ));
    }

    @PostMapping("/executions/{executionId}/retry")
    @PreAuthorize("hasAuthority('ETL_EXECUTE')")
    public ResponseEntity<Map<String, Object>> retry(
            @PathVariable Long executionId
    ) {
        Long retryExecutionId = executionService.requestRetry(executionId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "executionId", retryExecutionId,
                        "previousExecutionId", executionId,
                        "triggerType", "RETRY",
                        "status", "PENDING"
                ));
    }
}
