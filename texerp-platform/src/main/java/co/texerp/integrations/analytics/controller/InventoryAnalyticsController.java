
package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.dto.InventoryAnalyticsResponse;
import co.texerp.integrations.analytics.service.InventoryAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics/inventory")
@RequiredArgsConstructor
public class InventoryAnalyticsController {

    private final InventoryAnalyticsService service;

    @GetMapping("/analytics")
    public InventoryAnalyticsResponse analytics(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(required = false)
            Long productId,

            @RequestParam(required = false)
            Long warehouseId,

            @RequestParam(required = false)
            String fabricType,

            @RequestParam(defaultValue = "30")
            int inactiveDays,

            @RequestParam(defaultValue = "10")
            int limit
    ) {
        try {
            return service.getAnalytics(
                    from,
                    to,
                    productId,
                    warehouseId,
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
