
package co.texerp.integrations.analytics.service;

import co.texerp.integrations.analytics.dto.InventoryClosureResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class InventoryClosureService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("America/Bogota");

    private final InventorySnapshotService snapshotService;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventoryClosureResult closeToday(String username) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "El usuario de cierre es obligatorio"
            );
        }

        // El servicio de captura adquiere el bloqueo
        // de la fecha y actualiza la fotografía preliminar.
        var snapshot = snapshotService.captureToday();

        LocalDate snapshotDate = snapshot.snapshotDate();

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("snapshotDate", snapshotDate)
                        .addValue("username", username);

        int updated = jdbc.update("""
            UPDATE texerp_dw.inventory_snapshot_closure
            SET status = 'CLOSED',
                closed_at = NOW(),
                closed_by = :username
            WHERE snapshot_date = :snapshotDate
              AND status = 'PRELIMINARY'
            """, params);

        if (updated != 1) {
            throw new IllegalStateException(
                    "No fue posible cerrar el inventario"
            );
        }

        return new InventoryClosureResult(
                snapshotDate,
                "CLOSED",
                snapshot.totalRows()
        );
    }
}
