
package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.dto.InventoryDashboardResponse;
import co.texerp.integrations.analytics.service.InventoryDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics/inventory")
public class InventoryDashboardController {

    private final InventoryDashboardService service;

    @GetMapping("/dashboard")
    public InventoryDashboardResponse dashboard(
            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate from,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate to,

            @RequestParam(required = false)
            Long warehouseId,

            @RequestParam(required = false)
            Long productId,

            @RequestParam(required = false)
            String fabricType,

            @RequestParam(defaultValue = "30")
            int inactiveDays,

            @RequestParam(defaultValue = "10")
            int limit
    ) {
        try {
            return service.getDashboard(
                    from,
                    to,
                    warehouseId,
                    productId,
                    fabricType,
                    inactiveDays,
                    limit
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
