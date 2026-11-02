package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.dto.CustomerDashboardResponse;
import co.texerp.integrations.analytics.service.CustomerDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics/customers")
public class CustomerAnalyticsController {
    private final CustomerDashboardService service;

    @GetMapping("/dashboard")
    public CustomerDashboardResponse dashboard(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String type,
            @RequestParam(required=false) String segment,
            @RequestParam(required=false) Long productId,
            @RequestParam(defaultValue="10") int limit,
            @RequestParam(defaultValue="SALES") String rankingBy) {
        try { return service.getDashboard(from,to,type,segment,productId,limit,rankingBy); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage(),e); }
    }
}
