
package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.dto.InventoryEvolutionResponse;
import co.texerp.integrations.analytics.service.InventoryEvolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics/inventory")
@RequiredArgsConstructor
public class InventoryEvolutionController {

    private final InventoryEvolutionService service;

    @GetMapping("/evolution")
    public InventoryEvolutionResponse evolution(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(required = false)
            Long productId,

            @RequestParam(required = false)
            Long warehouseId
    ) {
        try {
            return service.getEvolution(
                    from,
                    to,
                    productId,
                    warehouseId
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage(),
                    ex
            );
        }
    }
}
