package co.texerp.integrations.controller;

import co.texerp.integrations.domain.AuditEvent;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.repository.AuditEventRepository;

import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditTrailController {

    private final AuditEventRepository repository;

    public AuditTrailController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<Page<AuditEvent>> search(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String action,
            @RequestParam(defaultValue = "") String entity,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Paginación inválida");
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<AuditEvent> result = repository.selectPage(
                search,
                action,
                entity,
                success,
                pageable
        );

        return ApiResponse.ok("Auditoría consultada", result);
    }
}