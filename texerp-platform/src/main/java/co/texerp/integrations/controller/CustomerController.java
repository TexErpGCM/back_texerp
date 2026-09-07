package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.CustomerDtos.CustomerPage;
import co.texerp.integrations.dto.CustomerDtos.CustomerRequest;
import co.texerp.integrations.dto.CustomerDtos.CustomerResponse;
import co.texerp.integrations.dto.CustomerDtos.CustomerStatusRequest;
import co.texerp.integrations.service.CustomerService;
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
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @Valid @RequestBody CustomerRequest request,
            HttpServletRequest httpRequest) {
        CustomerResponse customer = service.create(request, httpRequest);
        return ResponseEntity
                .created(URI.create("/api/v1/customers/" + customer.id()))
                .body(ApiResponse.ok("Cliente creado correctamente", customer));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                "Cliente actualizado correctamente",
                service.update(id, request, httpRequest)
        );
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<CustomerResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody CustomerStatusRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                "Estado del cliente actualizado correctamente",
                service.changeStatus(id, request.active(), httpRequest)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok("Cliente encontrado", service.findById(id));
    }

    @GetMapping
    public ApiResponse<CustomerPage> search(
            @RequestParam(defaultValue = "") String document,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(
                "Clientes consultados correctamente",
                service.search(document, name, type, active, page, size)
        );
    }
}
