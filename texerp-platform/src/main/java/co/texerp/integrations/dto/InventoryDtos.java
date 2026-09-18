package co.texerp.integrations.dto;

import co.texerp.integrations.domain.InventoryStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record MinimumStockRequest(
            @NotNull(message = "es obligatorio")
            @DecimalMin(value = "0.0", message = "debe ser igual o mayor a cero")
            BigDecimal minimum
    ) {
    }

    public record InventoryBalanceResponse(
            Long id,
            Long variantId,
            String sku,
            Long productId,
            String productCode,
            String productName,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            BigDecimal available,
            BigDecimal reserved,
            BigDecimal minimum,
            InventoryStatus status,
            boolean lowStock,
            Instant updatedAt
    ) {
    }

    public record InventoryPage(
            List<InventoryBalanceResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record SkuInventoryResponse(
            Long variantId,
            String sku,
            Long productId,
            String productCode,
            String productName,
            List<InventoryBalanceResponse> balances
    ) {
    }
}
