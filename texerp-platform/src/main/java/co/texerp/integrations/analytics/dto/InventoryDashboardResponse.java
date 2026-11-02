
package co.texerp.integrations.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InventoryDashboardResponse(
        LocalDate from,
        LocalDate to,
        boolean hasData,
        boolean historicalInventoryAvailable,
        Summary summary,
        List<WarehouseStock> warehouses,
        List<CriticalStock> criticalProducts,
        List<MovementSummary> movements,
        List<InactiveProduct> inactiveProducts
) {

    public record Summary(
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            BigDecimal availableInventoryValue,
            long skuWarehouseCount,
            long belowMinimumCount,
            BigDecimal belowMinimumUnits,
            BigDecimal movementEntries,
            BigDecimal movementExits,
            BigDecimal netAdjustments,
            BigDecimal netMovement
    ) {}

    public record WarehouseStock(
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            BigDecimal inventoryValue,
            long belowMinimumCount
    ) {}

    public record CriticalStock(
            Long variantId,
            String sku,
            String productName,
            Long warehouseId,
            String warehouseName,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            BigDecimal minimumQuantity,
            BigDecimal shortage
    ) {}

    public record MovementSummary(
            String movementType,
            long movements,
            BigDecimal positiveQuantity,
            BigDecimal negativeQuantity,
            BigDecimal netQuantity,
            BigDecimal reservedDelta
    ) {}

    public record InactiveProduct(
            Long variantId,
            String sku,
            String productName,
            Long warehouseId,
            String warehouseName,
            BigDecimal availableQuantity,
            LocalDate lastMovementDate,
            Long daysWithoutMovement
    ) {}
}
