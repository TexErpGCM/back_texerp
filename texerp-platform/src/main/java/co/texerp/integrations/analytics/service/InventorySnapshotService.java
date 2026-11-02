
package co.texerp.integrations.analytics.service;

import co.texerp.integrations.analytics.dto.InventorySnapshotResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class InventorySnapshotService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("America/Bogota");

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventorySnapshotResult captureToday() {

        OffsetDateTime capturedAt =
                OffsetDateTime.now(BUSINESS_ZONE);

        LocalDate snapshotDate =
                capturedAt.toLocalDate();

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("snapshotDate", snapshotDate)
                        .addValue("capturedAt", capturedAt);

        jdbc.update("""
            INSERT INTO texerp_dw.inventory_snapshot_closure (
                snapshot_date,
                status
            )
            VALUES (:snapshotDate, 'PRELIMINARY')
            ON CONFLICT (snapshot_date) DO NOTHING
            """, params);

        String status = jdbc.queryForObject("""
            SELECT status
            FROM texerp_dw.inventory_snapshot_closure
            WHERE snapshot_date = :snapshotDate
            FOR UPDATE
            """, params, String.class);

        if (!"PRELIMINARY".equals(status)) {
            throw new IllegalStateException(
                    "El inventario del " + snapshotDate +
                            " ya está cerrado"
            );
        }

        // Reemplaza únicamente la fotografía preliminar.
        jdbc.update("""
            DELETE FROM texerp_dw.fact_inventory
            WHERE snapshot_date = :snapshotDate
            """, params);

        int insertedRows = jdbc.update("""
            INSERT INTO texerp_dw.fact_inventory (
                snapshot_date,
                variant_id,
                warehouse_id,
                available_quantity,
                reserved_quantity,
                minimum_quantity,
                unit_cost_snapshot,
                available_inventory_value,
                source_updated_at,
                captured_at
            )
            SELECT
                :snapshotDate,
                ib.variant_id,
                ib.warehouse_id,
                ib.available_quantity,
                ib.reserved_quantity,
                ib.minimum_quantity,
                pv.cost,
                CASE
                    WHEN pv.cost IS NULL THEN NULL
                    ELSE ib.available_quantity * pv.cost
                END,
                ib.updated_at,
                :capturedAt
            FROM public.inventory_balance ib
            JOIN public.product_variant pv
                ON pv.id = ib.variant_id
            JOIN public.warehouse w
                ON w.id = ib.warehouse_id
            """, params);

        return new InventorySnapshotResult(
                snapshotDate,
                insertedRows,
                insertedRows,
                capturedAt
        );
    }
}
