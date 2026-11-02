package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.PurchaseReceiptDtos.*;
import co.texerp.integrations.service.PurchaseReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
public class PurchaseReceiptController {
    private final PurchaseReceiptService service;
    public PurchaseReceiptController(PurchaseReceiptService service) { this.service = service; }

    @PostMapping("/api/v1/purchase-orders/{orderId}/receipts")
    public ResponseEntity<ApiResponse<Response>> receive(@PathVariable Long orderId,
                                                         @Valid @RequestBody CreateRequest request) {
        Response result = service.receive(orderId, request);
        return ResponseEntity.created(URI.create("/api/v1/purchase-receipts/" + result.id()))
                .body(ApiResponse.ok("Mercancía recibida", result));
    }

    @GetMapping("/api/v1/purchase-receipts/{id}")
    public ApiResponse<Response> findById(@PathVariable Long id) {
        return ApiResponse.ok("Recepción encontrada", service.findById(id));
    }

    @GetMapping("/api/v1/purchase-orders/{orderId}/receipts")
    public ApiResponse<List<Response>> byOrder(@PathVariable Long orderId) {
        return ApiResponse.ok("Recepciones de la orden", service.byOrder(orderId));
    }
}
