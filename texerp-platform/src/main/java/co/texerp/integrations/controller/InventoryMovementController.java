package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.InventoryMovementDtos.CompensationRequest;
import co.texerp.integrations.dto.InventoryMovementDtos.InventoryMovementPage;
import co.texerp.integrations.dto.InventoryMovementDtos.InventoryMovementResponse;
import co.texerp.integrations.service.InventoryMovementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/inventory/movements")
public class InventoryMovementController {

    private final InventoryMovementService service;

    public InventoryMovementController(InventoryMovementService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<InventoryMovementPage> search(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam(defaultValue = "") String sku,
            @RequestParam(defaultValue = "") String warehouse,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String document,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(
                "Movimientos de inventario consultados correctamente",
                service.search(from, to, sku, warehouse, type, document, page, size)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryMovementResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(
                "Movimiento de inventario consultado correctamente",
                service.findById(id)
        );
    }

    @PostMapping("/{id}/compensations")
    public ApiResponse<InventoryMovementResponse> compensate(
            @PathVariable Long id,
            @Valid @RequestBody CompensationRequest request
    ) {
        return ApiResponse.ok(
                "Movimiento compensatorio generado correctamente",
                service.compensate(id, request.sourceDocument(), request.reason())
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> rejectUpdate(@PathVariable Long id) {
        service.rejectMutation(id);
        return ApiResponse.ok("Operación no permitida", null);
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> rejectPatch(@PathVariable Long id) {
        service.rejectMutation(id);
        return ApiResponse.ok("Operación no permitida", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> rejectDelete(@PathVariable Long id) {
        service.rejectMutation(id);
        return ApiResponse.ok("Operación no permitida", null);
    }
}
