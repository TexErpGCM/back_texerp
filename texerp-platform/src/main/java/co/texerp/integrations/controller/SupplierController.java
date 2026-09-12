package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.SupplierDtos.SupplierPage;
import co.texerp.integrations.dto.SupplierDtos.SupplierRequest;
import co.texerp.integrations.dto.SupplierDtos.SupplierResponse;
import co.texerp.integrations.dto.SupplierDtos.SupplierStatusRequest;
import co.texerp.integrations.service.SupplierService;
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
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierResponse>> create(
            @Valid @RequestBody SupplierRequest request,
            HttpServletRequest httpRequest) {
        SupplierResponse supplier = service.create(request, httpRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/suppliers/" + supplier.id()))
                .body(ApiResponse.ok("Proveedor creado correctamente", supplier));
    }

    @PutMapping("/{id}")
    public ApiResponse<SupplierResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                "Proveedor actualizado correctamente",
                service.update(id, request, httpRequest)
        );
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<SupplierResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody SupplierStatusRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                "Estado del proveedor actualizado correctamente",
                service.changeStatus(id, request.active(), httpRequest)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<SupplierResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok("Proveedor encontrado", service.findById(id));
    }

    @GetMapping
    public ApiResponse<SupplierPage> search(
            @RequestParam(defaultValue = "") String taxId,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(
                "Proveedores consultados correctamente",
                service.search(taxId, name, active, page, size)
        );
    }
}
