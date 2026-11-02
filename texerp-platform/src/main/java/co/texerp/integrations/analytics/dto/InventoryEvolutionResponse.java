
package co.texerp.integrations.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InventoryEvolutionResponse(
        LocalDate from,
        LocalDate to,
        Long productId,
        Long warehouseId,
        boolean hasData,
        boolean hasComparison,
        int totalSnapshots,
        Summary summary,
        List<DailyPoint> evolution
) {

    public record Summary(
            BigDecimal initialQuantity,
            BigDecimal finalQuantity,
            BigDecimal quantityChange,
            BigDecimal quantityChangePercentage,
            BigDecimal initialValue,
            BigDecimal finalValue,
            BigDecimal valueChange,
            BigDecimal valueChangePercentage
    ) {}

    public record DailyPoint(
            LocalDate date,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            BigDecimal inventoryValue,
            long balanceCount,
            long belowMinimumCount,
            long missingCostCount
    ) {}
}
