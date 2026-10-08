package co.texerp.integrations.controller;

import co.texerp.integrations.domain.QuotationStatus;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.QuotationDtos.*;
import co.texerp.integrations.dto.SaleDtos.SaleResponse;
import co.texerp.integrations.service.QuotationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController {

    private final QuotationService service;

    public QuotationController(QuotationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuotationResponse>> create(
            @Valid @RequestBody QuotationCreateRequest request
    ) {

        QuotationResponse quotation = service.create(request);

        return ResponseEntity
                .created(URI.create("/api/v1/quotations/" + quotation.id()))
                .body(
                        ApiResponse.ok(
                                "Cotización creada correctamente",
                                quotation
                        )
                );
    }

    @GetMapping
    public ApiResponse<Page<QuotationResponse>> findAll(
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) QuotationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<QuotationResponse> quotations = service.findAll(
                number,
                customer,
                status,
                pageable
        );

        return ApiResponse.ok(
                "Cotizaciones consultadas correctamente",
                quotations
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<QuotationResponse> findById(
            @PathVariable Long id
    ) {

        return ApiResponse.ok(
                "Cotización encontrada",
                service.findById(id)
        );
    }

    @PatchMapping("/{id}/send")
    public ApiResponse<QuotationResponse> send(
            @PathVariable Long id
    ) {

        return ApiResponse.ok(
                "Cotización enviada correctamente",
                service.send(id)
        );
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<QuotationResponse> cancel(
            @PathVariable Long id
    ) {

        return ApiResponse.ok(
                "Cotización cancelada correctamente",
                service.cancel(id)
        );
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<ApiResponse<SaleResponse>> convert(
            @PathVariable Long id,
            @Valid @RequestBody ConvertQuotationRequest request
    ) {

        SaleResponse sale = service.convert(id, request);

        return ResponseEntity
                .created(URI.create("/api/v1/sales/" + sale.id()))
                .body(
                        ApiResponse.ok(
                                "Cotización convertida en venta correctamente",
                                sale
                        )
                );
    }
}