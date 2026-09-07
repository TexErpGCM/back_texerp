package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.ProductVariantDtos.*;
import co.texerp.integrations.service.ProductVariantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
public class ProductVariantController {
    private final ProductVariantService service;
    public ProductVariantController(ProductVariantService service) { this.service = service; }

    @PostMapping("/products/{productId}/variants")
    public ResponseEntity<ApiResponse<VariantResponse>> create(@PathVariable Long productId,
                                                               @Valid @RequestBody VariantRequest request, HttpServletRequest http) {
        VariantResponse result = service.create(productId, request, http);
        return ResponseEntity.created(URI.create("/api/v1/variants/" + result.id()))
                .body(ApiResponse.ok("Variante creada correctamente", result));
    }

    @PutMapping("/variants/{id}")
    public ApiResponse<VariantResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody VariantRequest request, HttpServletRequest http) {
        return ApiResponse.ok("Variante actualizada correctamente", service.update(id, request, http));
    }

    @PatchMapping("/variants/{id}/price-cost")
    public ApiResponse<VariantResponse> updatePriceCost(@PathVariable Long id,
                                                        @Valid @RequestBody PriceCostRequest request, HttpServletRequest http) {
        return ApiResponse.ok("Precio y costo actualizados correctamente", service.updatePriceCost(id, request, http));
    }

    @PatchMapping("/variants/{id}/status")
    public ApiResponse<VariantResponse> changeStatus(@PathVariable Long id,
                                                     @Valid @RequestBody VariantStatusRequest request, HttpServletRequest http) {
        return ApiResponse.ok("Estado de la variante actualizado correctamente",
                service.changeStatus(id, request.active(), http));
    }

    @GetMapping("/variants/{id}")
    public ApiResponse<VariantResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok("Variante encontrada", service.findById(id));
    }

    @GetMapping("/variants")
    public ApiResponse<VariantPage> search(@RequestParam(required = false) Long productId,
                                           @RequestParam(defaultValue = "") String sku,
                                           @RequestParam(defaultValue = "") String color,
                                           @RequestParam(required = false) Boolean active,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Variantes consultadas correctamente",
                service.search(productId, sku, color, active, page, size));
    }
}
