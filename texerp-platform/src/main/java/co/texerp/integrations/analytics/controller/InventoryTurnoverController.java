
package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.dto.InventoryTurnoverResponse;
import co.texerp.integrations.analytics.service.InventoryTurnoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics/inventory")
@RequiredArgsConstructor
public class InventoryTurnoverController {

    private final InventoryTurnoverService service;

    @GetMapping("/turnover")
    public InventoryTurnoverResponse turnover(
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
            return service.getTurnover(
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
