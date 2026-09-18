package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.InventoryAdjustmentDtos.InventoryAdjustmentRequest;
import co.texerp.integrations.dto.InventoryAdjustmentDtos.InventoryAdjustmentResponse;
import co.texerp.integrations.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/adjustments")
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService service;

    public InventoryAdjustmentController(InventoryAdjustmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> create(
            @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        "Ajuste de inventario registrado correctamente",
                        service.create(request)
                ));
    }
}
