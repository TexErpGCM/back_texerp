
package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.dto.SalesDashboardResponse;
import co.texerp.integrations.analytics.service.SalesDashboardService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics/sales")
public class SalesDashboardController {

    private final SalesDashboardService service;

    public SalesDashboardController(
            SalesDashboardService service
    ) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SalesDashboardResponse> dashboard(

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(required = false)
            Long productId,

            @RequestParam(required = false)
            Long customerId,

            @RequestParam(required = false)
            Long warehouseId,

            @RequestParam(required = false)
            String fabricType,

            @RequestParam(defaultValue = "10")
            int limit,

            @RequestParam(defaultValue = "SALES")
            String rankingBy
    ) {

        try {
            SalesDashboardResponse response =
                    service.getDashboard(
                            from,
                            to,
                            productId,
                            customerId,
                            warehouseId,
                            fabricType,
                            limit,
                            rankingBy
                    );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage(),
                    ex
            );
        }
    }
}
