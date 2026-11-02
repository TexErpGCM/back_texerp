package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.dto.PurchaseDashboardResponse;
import co.texerp.integrations.analytics.service.PurchaseDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics/purchases")
public class PurchaseAnalyticsController {
    private final PurchaseDashboardService service;

    @GetMapping("/dashboard")
    public PurchaseDashboardResponse dashboard(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Long supplierId,
            @RequestParam(required=false) Long productId,
            @RequestParam(required=false) Long warehouseId,
            @RequestParam(defaultValue="10") int limit,
            @RequestParam(defaultValue="AMOUNT") String rankingBy) {
        try { return service.getDashboard(from,to,supplierId,productId,warehouseId,limit,rankingBy); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage(),e); }
    }
}
