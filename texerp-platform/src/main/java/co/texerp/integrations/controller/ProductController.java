package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.ProductDtos.ProductPage;
import co.texerp.integrations.dto.ProductDtos.ProductRequest;
import co.texerp.integrations.dto.ProductDtos.ProductResponse;
import co.texerp.integrations.dto.ProductDtos.ProductStatusRequest;
import co.texerp.integrations.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody ProductRequest request, HttpServletRequest httpRequest) {
        ProductResponse product = service.create(request, httpRequest);
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id()))
                .body(ApiResponse.ok("Producto creado correctamente", product));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ProductRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok("Producto actualizado correctamente", service.update(id, request, httpRequest));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ProductResponse> changeStatus(@PathVariable Long id,
                                                     @Valid @RequestBody ProductStatusRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok("Estado del producto actualizado correctamente",
                service.changeStatus(id, request.active(), httpRequest));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok("Producto encontrado", service.findById(id));
    }

    @GetMapping
    public ApiResponse<ProductPage> search(
            @RequestParam(defaultValue = "") String code,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String fabricType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Catálogo consultado correctamente",
                service.search(code, name, fabricType, active, page, size));
    }
}
