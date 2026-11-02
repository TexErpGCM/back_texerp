
package co.texerp.integrations.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.Types;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryHistoryService {

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<HistoryRow> findHistory(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long warehouseId,
            boolean closedOnly
    ) {

        // 1. Validar fechas.
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "Las fechas from y to son obligatorias"
            );
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "La fecha inicial no puede ser mayor que la final"
            );
        }

        // 2. Validar filtros opcionales.
        if (productId != null && productId <= 0) {
            throw new IllegalArgumentException(
                    "productId debe ser positivo"
            );
        }

        if (warehouseId != null && warehouseId <= 0) {
            throw new IllegalArgumentException(
                    "warehouseId debe ser positivo"
            );
        }

        // 3. Parámetros obligatorios.
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("from", from, Types.DATE)
                        .addValue("to", to, Types.DATE);

        // 4. Construir condiciones dinámicas.
        StringBuilder filters = new StringBuilder();

        if (productId != null) {
            filters.append("""
                    AND pv.product_id = :productId
                    """);

            params.addValue(
                    "productId",
                    productId,
                    Types.BIGINT
            );
        }

        if (warehouseId != null) {
            filters.append("""
                    AND f.warehouse_id = :warehouseId
                    """);

            params.addValue(
                    "warehouseId",
                    warehouseId,
                    Types.BIGINT
            );
        }

        if (closedOnly) {
            filters.append("""
                    AND c.status = 'CLOSED'
                    """);
        }

        // 5. Consultar las fotografías históricas.
        String sql = """
                SELECT
                    f.snapshot_date,
                    c.status,

                    COALESCE(
                        SUM(f.available_quantity),
                        0
                    ) AS available,

                    COALESCE(
                        SUM(f.reserved_quantity),
                        0
                    ) AS reserved,

                    SUM(
                        f.available_inventory_value
                    ) AS inventory_value,

                    COUNT(*) AS balance_count,

                    COUNT(*) FILTER (
                        WHERE f.unit_cost_snapshot IS NULL
                    ) AS missing_cost_count,

                    COUNT(*) FILTER (
                        WHERE f.available_quantity
                            < f.minimum_quantity
                    ) AS below_minimum_count

                FROM texerp_dw.fact_inventory f

                INNER JOIN texerp_dw.inventory_snapshot_closure c
                    ON c.snapshot_date = f.snapshot_date

                INNER JOIN public.product_variant pv
                    ON pv.id = f.variant_id

                WHERE f.snapshot_date BETWEEN :from AND :to
                """
                + filters
                + """
                
                GROUP BY
                    f.snapshot_date,
                    c.status

                ORDER BY
                    f.snapshot_date ASC
                """;

        // 6. Mapear los resultados.
        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new HistoryRow(
                        rs.getObject(
                                "snapshot_date",
                                LocalDate.class
                        ),
                        rs.getString("status"),
                        rs.getBigDecimal("available"),
                        rs.getBigDecimal("reserved"),
                        rs.getBigDecimal("inventory_value"),
                        rs.getLong("balance_count"),
                        rs.getLong("missing_cost_count"),
                        rs.getLong("below_minimum_count")
                )
        );
    }

    /**
     * Indicadores históricos agrupados por fecha.
     *
     * inventoryValue utiliza el costo capturado
     * en cada fotografía, no el costo actual.
     *
     * missingCostCount indica cuántos registros
     * no tienen costo registrado.
     */
    public record HistoryRow(
            LocalDate snapshotDate,
            String status,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            BigDecimal inventoryValue,
            long balanceCount,
            long missingCostCount,
            long belowMinimumCount
    ) {}
}
