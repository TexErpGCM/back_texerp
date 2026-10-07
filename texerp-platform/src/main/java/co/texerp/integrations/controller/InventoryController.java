package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.InventoryDtos.InventoryBalanceResponse;
import co.texerp.integrations.dto.InventoryDtos.InventoryPage;
import co.texerp.integrations.dto.InventoryDtos.MinimumStockRequest;
import co.texerp.integrations.dto.InventoryDtos.SkuInventoryResponse;
import co.texerp.integrations.service.InventoryService;
import co.texerp.integrations.service.InventoryReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService service;
    private final InventoryReportService reportService;

    public InventoryController(
            InventoryService service,
            InventoryReportService reportService
    ) {
        this.service = service;
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<InventoryPage> search(
            @RequestParam(defaultValue = "") String sku,
            @RequestParam(defaultValue = "") String product,
            @RequestParam(defaultValue = "") String warehouse,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(
                "Inventario consultado correctamente",
                service.search(sku, product, warehouse, status, lowStock, page, size)
        );
    }

    /**
     * Vista específica para abastecimiento. Devuelve únicamente balances cuyo
     * disponible es menor o igual al mínimo, incluyendo existencias en cero.
     * El orden por bodega permite agrupar fácilmente los resultados.
     */
    @GetMapping("/low-stock")
    public ApiResponse<InventoryPage> findLowStock(
            @RequestParam(defaultValue = "") String product,
            @RequestParam(defaultValue = "") String warehouse,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(
                "Variantes con inventario bajo consultadas correctamente",
                service.findLowStock(product, warehouse, page, size)
        );
    }

    @GetMapping("/sku/{sku}")
    public ApiResponse<SkuInventoryResponse> findBySku(@PathVariable String sku) {
        return ApiResponse.ok(
                "Inventario por SKU consultado correctamente",
                service.findBySku(sku)
        );
    }

    @GetMapping("/reports/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(defaultValue = "") String sku,
            @RequestParam(defaultValue = "") String product,
            @RequestParam(defaultValue = "") String warehouse,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        byte[] report = reportService.generateExcel(
                sku, product, warehouse, status, lowStock, page, size
        );

        return downloadResponse(
                report,
                "inventario.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }

    @GetMapping("/reports/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(defaultValue = "") String sku,
            @RequestParam(defaultValue = "") String product,
            @RequestParam(defaultValue = "") String warehouse,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        byte[] report = reportService.generatePdf(
                sku, product, warehouse, status, lowStock, page, size
        );

        return downloadResponse(report, "inventario.pdf", MediaType.APPLICATION_PDF_VALUE);
    }

    private ResponseEntity<byte[]> downloadResponse(byte[] content, String filename, String contentType) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(content.length)
                .body(content);
    }

    @PatchMapping("/variants/{variantId}/warehouses/{warehouseId}/minimum")
    public ApiResponse<InventoryBalanceResponse> configureMinimum(
            @PathVariable Long variantId,
            @PathVariable Long warehouseId,
            @Valid @RequestBody MinimumStockRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.ok(
                "Mínimo de inventario actualizado correctamente",
                service.configureMinimum(variantId, warehouseId, request.minimum(), httpRequest)
        );
    }
}
