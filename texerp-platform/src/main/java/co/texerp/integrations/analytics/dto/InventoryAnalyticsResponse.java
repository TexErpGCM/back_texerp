
package co.texerp.integrations.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record InventoryAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        boolean hasData,
        InventoryDashboardResponse operational,
        InventoryEvolutionResponse evolution,
        InventoryTurnoverResponse turnover,
        List<SnapshotStatus> snapshots
) {

    public record SnapshotStatus(
            LocalDate date,
            String status,
            boolean closed
    ) {}
}
