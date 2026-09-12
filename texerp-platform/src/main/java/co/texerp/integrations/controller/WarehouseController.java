package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.WarehouseDtos.WarehousePage;
import co.texerp.integrations.dto.WarehouseDtos.WarehouseRequest;
import co.texerp.integrations.dto.WarehouseDtos.WarehouseResponse;
import co.texerp.integrations.dto.WarehouseDtos.WarehouseStatusRequest;
import co.texerp.integrations.service.WarehouseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(
            @Valid @RequestBody WarehouseRequest request,
            HttpServletRequest httpRequest) {
        WarehouseResponse warehouse = service.create(request, httpRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/warehouses/" + warehouse.id()))
                .body(ApiResponse.ok("Bodega creada correctamente", warehouse));
    }

    @PutMapping("/{id}")
    public ApiResponse<WarehouseResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                "Bodega actualizada correctamente",
                service.update(id, request, httpRequest)
        );
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<WarehouseResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseStatusRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                "Estado de la bodega actualizado correctamente",
                service.changeStatus(id, request.active(), httpRequest)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<WarehouseResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok("Bodega encontrada", service.findById(id));
    }

    @GetMapping
    public ApiResponse<WarehousePage> search(
            @RequestParam(defaultValue = "") String code,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String location,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(
                "Bodegas consultadas correctamente",
                service.search(code, name, location, active, page, size)
        );
    }
}
