
package co.texerp.integrations.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryTurnoverResponse(
        LocalDate from,
        LocalDate to,
        Long productId,
        Long warehouseId,
        boolean hasData,
        boolean hasValidTurnover,
        String status,
        BigDecimal costOfGoodsSold,
        BigDecimal initialInventoryValue,
        BigDecimal finalInventoryValue,
        BigDecimal averageInventoryValue,
        BigDecimal turnover,
        Long initialSnapshotCount,
        Long finalSnapshotCount
) {}
