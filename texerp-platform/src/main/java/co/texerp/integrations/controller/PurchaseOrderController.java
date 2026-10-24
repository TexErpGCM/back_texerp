package co.texerp.integrations.controller;

import co.texerp.integrations.domain.PurchaseOrderStatus;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.PurchaseOrderDtos.*;
import co.texerp.integrations.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {
    private final PurchaseOrderService service;
    public PurchaseOrderController(PurchaseOrderService service) { this.service = service; }

    @GetMapping
    public ApiResponse<Page<Response>> findAll(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long variantId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ApiResponse.ok(
                "Órdenes encontradas",
                service.search(supplierId, status, from, to, variantId, pageable)
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Response>> create(@Valid @RequestBody CreateRequest request) {
        Response order = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/purchase-orders/" + order.id()))
                .body(ApiResponse.ok("Orden creada", order));
    }

    @GetMapping("/{id}")
    public ApiResponse<Response> findById(@PathVariable Long id) {
        return ApiResponse.ok("Orden encontrada", service.findById(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Response> approve(@PathVariable Long id) {
        return ApiResponse.ok("Orden aprobada", service.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Response> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return ApiResponse.ok("Orden rechazada", service.reject(id, request.reason()));
    }
}
