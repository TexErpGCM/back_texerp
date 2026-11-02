
package co.texerp.integrations.controller;

import co.texerp.integrations.domain.PurchaseOrderStatus;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.PurchaseOrderDtos.*;
import co.texerp.integrations.service.PurchaseOrderService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    /**
     * Consultar órdenes de compra con filtros y paginación.
     */
    @GetMapping
    public ApiResponse<Page<Response>> findAll(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Long variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        return ApiResponse.ok(
                "Órdenes encontradas",
                service.search(
                        supplierId,
                        status,
                        from,
                        to,
                        variantId,
                        page,
                        size
                )
        );
    }

    /**
     * Crear una nueva orden de compra.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Response>> create(
            @Valid @RequestBody CreateRequest request
    ) {

        Response order = service.create(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/v1/purchase-orders/" + order.id()
                        )
                )
                .body(
                        ApiResponse.ok(
                                "Orden creada",
                                order
                        )
                );
    }

    /**
     * Consultar una orden de compra por ID.
     */
    @GetMapping("/{id}")
    public ApiResponse<Response> findById(
            @PathVariable Long id
    ) {

        return ApiResponse.ok(
                "Orden encontrada",
                service.findById(id)
        );
    }

    /**
     * Aprobar una orden de compra.
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<Response> approve(
            @PathVariable Long id
    ) {

        return ApiResponse.ok(
                "Orden aprobada",
                service.approve(id)
        );
    }

    /**
     * Rechazar una orden de compra.
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<Response> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request
    ) {

        return ApiResponse.ok(
                "Orden rechazada",
                service.reject(id, request.reason())
        );
    }
}
