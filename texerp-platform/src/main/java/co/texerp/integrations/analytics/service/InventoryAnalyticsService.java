
package co.texerp.integrations.analytics.service;

import co.texerp.integrations.analytics.dto.InventoryAnalyticsResponse;
import co.texerp.integrations.analytics.dto.InventoryAnalyticsResponse.SnapshotStatus;
import co.texerp.integrations.analytics.dto.InventoryDashboardResponse;
import co.texerp.integrations.analytics.dto.InventoryEvolutionResponse;
import co.texerp.integrations.analytics.dto.InventoryTurnoverResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryAnalyticsService {

    private final InventoryDashboardService dashboardService;
    private final InventoryEvolutionService evolutionService;
    private final InventoryTurnoverService turnoverService;
    private final InventoryHistoryService historyService;

    @Transactional(readOnly = true)
    public InventoryAnalyticsResponse getAnalytics(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long warehouseId,
            String fabricType,
            int inactiveDays,
            int limit
    ) {

        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "El rango de fechas no es válido"
            );
        }

        // 1. Inventario operativo.
        InventoryDashboardResponse operational =
                dashboardService.getDashboard(
                        from,
                        to,
                        warehouseId,
                        productId,
                        fabricType,
                        inactiveDays,
                        limit
                );

        // 2. Evolución con los mismos filtros.
        InventoryEvolutionResponse evolution =
                evolutionService.getEvolution(
                        from,
                        to,
                        productId,
                        warehouseId,
                        fabricType
                );

        // 3. Rotación con los mismos filtros.
        InventoryTurnoverResponse turnover = null;

        if (from.isBefore(to)) {
            turnover = turnoverService.getTurnover(
                    from,
                    to,
                    productId,
                    warehouseId,
                    fabricType
            );
        }

        // 4. Fotografías históricas filtradas.
        List<SnapshotStatus> snapshots =
                historyService.findHistory(
                                from,
                                to,
                                productId,
                                warehouseId,
                                fabricType,
                                false
                        ).stream()
                        .map(row -> new SnapshotStatus(
                                row.snapshotDate(),
                                row.status(),
                                "CLOSED".equals(row.status())
                        ))
                        .toList();

        // 5. Disponibilidad general de datos.
        boolean hasData =
                operational.hasData()
                        || evolution.hasData();

        return new InventoryAnalyticsResponse(
                from,
                to,
                hasData,
                operational,
                evolution,
                turnover,
                snapshots
        );
    }
}
