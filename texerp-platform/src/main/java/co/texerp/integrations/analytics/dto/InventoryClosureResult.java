
package co.texerp.integrations.analytics.dto;

import java.time.LocalDate;

public record InventoryClosureResult(
        LocalDate snapshotDate,
        String status,
        long totalRows
) {}
