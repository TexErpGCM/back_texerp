
package co.texerp.integrations.controller;

import co.texerp.integrations.domain.SaleStatus;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.PaymentDtos.PaymentCreateRequest;
import co.texerp.integrations.dto.SaleDtos.*;
import co.texerp.integrations.dto.SaleTransitionDtos;
import co.texerp.integrations.service.PaymentService;
import co.texerp.integrations.service.SaleService;
import co.texerp.integrations.service.SaleTransitionService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final SaleTransitionService transitions;
    private final SaleService saleService;
    private final PaymentService paymentService;

    public SaleController(
            SaleService saleService,
            PaymentService paymentService,
            SaleTransitionService transitions
    ) {
        this.transitions = transitions;
        this.saleService = saleService;
        this.paymentService = paymentService;
    }

    // CREAR Y CONFIRMAR VENTA
    @PostMapping
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public ResponseEntity<ApiResponse<SaleResponse>> create(
            @Valid @RequestBody SaleCreateRequest request
    ) {
        SaleResponse sale = saleService.create(request);

        return ResponseEntity
                .created(URI.create("/api/v1/sales/" + sale.id()))
                .body(ApiResponse.ok(
                        "Venta confirmada correctamente",
                        sale
                ));
    }

    // DESPACHAR VENTA
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('SALE_DISPATCH')")
    public ApiResponse<SaleResponse> dispatch(
            @PathVariable Long id,
            @RequestBody(required = false)
            SaleTransitionDtos.DispatchRequest request
    ) {
        return ApiResponse.ok(
                "Venta despachada",
                transitions.dispatch(
                        id,
                        request == null ? null : request.evidence()
                )
        );
    }

    // CONFIRMAR ENTREGA
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAuthority('SALE_DELIVER')")
    public ApiResponse<SaleResponse> deliver(
            @PathVariable Long id
    ) {
        return ApiResponse.ok(
                "Entrega confirmada",
                transitions.deliver(id)
        );
    }

    // CANCELAR VENTA
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALE_CANCEL')")
    public ApiResponse<SaleResponse> cancel(
            @PathVariable Long id,
            @Valid @RequestBody
            SaleTransitionDtos.CancelRequest request
    ) {
        return ApiResponse.ok(
                "Venta cancelada",
                transitions.cancel(id, request.reason())
        );
    }

    // CONSULTAR VENTA POR ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALE_READ')")
    public ApiResponse<SaleResponse> findById(
            @PathVariable Long id
    ) {
        return ApiResponse.ok(
                "Venta encontrada",
                saleService.findById(id)
        );
    }

    // REGISTRAR PAGO
    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<ApiResponse<SaleResponse>> registerPayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        SaleResponse sale = paymentService.register(id, request);

        return ResponseEntity
                .status(201)
                .body(ApiResponse.ok(
                        "Pago registrado correctamente",
                        sale
                ));
    }

    // LISTAR VENTAS
    @GetMapping
    @PreAuthorize("hasAuthority('SALE_READ')")
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
                Sort.by(
                        Sort.Direction.DESC,
                        "confirmedAt"
                )
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
