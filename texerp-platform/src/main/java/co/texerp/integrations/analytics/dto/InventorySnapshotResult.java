
package co.texerp.integrations.analytics.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record InventorySnapshotResult(
        LocalDate snapshotDate,
        int insertedRows,
        long totalRows,
        OffsetDateTime capturedAt
) {}
