package co.texerp.integrations.controller;

import co.texerp.integrations.domain.SaleStatus;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.PaymentDtos.PaymentCreateRequest;
import co.texerp.integrations.dto.SaleDtos.*;
import co.texerp.integrations.service.PaymentService;
import co.texerp.integrations.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {
    private final SaleService saleService;
    private final PaymentService paymentService;

    public SaleController(SaleService saleService, PaymentService paymentService) {
        this.saleService = saleService;
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SaleResponse>> create(@Valid @RequestBody SaleCreateRequest request) {
        SaleResponse sale = saleService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/sales/" + sale.id()))
                .body(ApiResponse.ok("Venta confirmada correctamente", sale));
    }

    @GetMapping("/{id}")
    public ApiResponse<SaleResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok("Venta encontrada", saleService.findById(id));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<SaleResponse>> registerPayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        SaleResponse sale = paymentService.register(id, request);
        return ResponseEntity.status(201)
                .body(ApiResponse.ok("Pago registrado correctamente", sale));
    }

    @GetMapping
    public ApiResponse<Page<SaleResponse>> findAll(
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "confirmedAt")
        );

        Page<SaleResponse> sales = saleService.findAll(
                number,
                customer,
                status,
                pageable
        );

        return ApiResponse.ok(
                "Ventas consultadas correctamente",
                sales
        );
    }
}
