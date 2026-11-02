
package co.texerp.integrations.analytics.service;

import co.texerp.integrations.analytics.dto.InventoryEvolutionResponse;
import co.texerp.integrations.analytics.dto.InventoryEvolutionResponse.DailyPoint;
import co.texerp.integrations.analytics.dto.InventoryEvolutionResponse.Summary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryEvolutionService {

    private static final BigDecimal ONE_HUNDRED =
            BigDecimal.valueOf(100);

    private final InventoryHistoryService historyService;

    @Transactional(readOnly = true)
    public InventoryEvolutionResponse getEvolution(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long warehouseId
    ) {
        return getEvolution(
                from, to, productId, warehouseId, null
        );
    }

    @Transactional(readOnly = true)
    public InventoryEvolutionResponse getEvolution(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long warehouseId,
            String fabricType
    ) {

        List<InventoryHistoryService.HistoryRow> history =
                historyService.findHistory(
                        from,
                        to,
                        productId,
                        warehouseId,
                        fabricType,
                        true
                );

        List<DailyPoint> points = history.stream()
                .map(row -> new DailyPoint(
                        row.snapshotDate(),
                        row.availableQuantity(),
                        row.reservedQuantity(),
                        row.inventoryValue(),
                        row.balanceCount(),
                        row.belowMinimumCount(),
                        row.missingCostCount()
                ))
                .toList();

        if (points.isEmpty()) {
            return new InventoryEvolutionResponse(
                    from,
                    to,
                    productId,
                    warehouseId,
                    false,
                    false,
                    0,
                    null,
                    points
            );
        }

        DailyPoint first = points.get(0);
        DailyPoint last = points.get(points.size() - 1);

        boolean hasComparison = points.size() >= 2;

        BigDecimal initialQuantity = first.availableQuantity();
        BigDecimal finalQuantity = last.availableQuantity();

        BigDecimal initialValue = first.inventoryValue();
        BigDecimal finalValue = last.inventoryValue();

        BigDecimal quantityChange = hasComparison
                ? finalQuantity.subtract(initialQuantity)
                : null;

        BigDecimal quantityChangePercentage = hasComparison
                ? percentage(initialQuantity, finalQuantity)
                : null;

        boolean completeValuation = points.stream().allMatch(
                point -> point.missingCostCount() == 0
                        && point.inventoryValue() != null
        );

        BigDecimal valueChange =
                hasComparison && completeValuation
                        ? finalValue.subtract(initialValue)
                        : null;

        BigDecimal valueChangePercentage =
                hasComparison && completeValuation
                        ? percentage(initialValue, finalValue)
                        : null;

        Summary summary = new Summary(
                initialQuantity,
                finalQuantity,
                quantityChange,
                quantityChangePercentage,
                initialValue,
                finalValue,
                valueChange,
                valueChangePercentage
        );

        return new InventoryEvolutionResponse(
                from,
                to,
                productId,
                warehouseId,
                true,
                hasComparison,
                points.size(),
                summary,
                points
        );
    }

    private BigDecimal percentage(
            BigDecimal initial,
            BigDecimal current
    ) {
        if (initial == null
                || current == null
                || initial.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return current.subtract(initial)
                .multiply(ONE_HUNDRED)
                .divide(
                        initial,
                        2,
                        RoundingMode.HALF_UP
                );
    }
}
