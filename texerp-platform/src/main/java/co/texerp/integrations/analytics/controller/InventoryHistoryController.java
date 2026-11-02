
package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.service.InventoryHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics/inventory")
@RequiredArgsConstructor
public class InventoryHistoryController {

    private final InventoryHistoryService service;

    @GetMapping("/history")
    public List<InventoryHistoryService.HistoryRow> history(
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

            @RequestParam(defaultValue = "false")
            boolean closedOnly
    ) {
        try {
            return service.findHistory(
                    from,
                    to,
                    productId,
                    warehouseId,
                    closedOnly
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
