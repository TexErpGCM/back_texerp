package co.texerp.integrations.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryHistoryRow(
        LocalDate date, String status,
        BigDecimal availableQuantity, BigDecimal reservedQuantity,
        BigDecimal inventoryValue, long skuWarehouseCount,
        long belowMinimumCount, long missingCostCount
) {}
