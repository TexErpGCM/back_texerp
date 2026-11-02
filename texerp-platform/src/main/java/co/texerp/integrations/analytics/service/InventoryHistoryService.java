
package co.texerp.integrations.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryHistoryService {

    private final NamedParameterJdbcTemplate jdbc;

    // Compatibilidad con los servicios existentes.
    @Transactional(readOnly = true)
    public List<HistoryRow> findHistory(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long warehouseId,
            boolean closedOnly
    ) {
        return findHistory(
                from,
                to,
                productId,
                warehouseId,
                null,
                closedOnly
        );
    }

    // Nueva versión con filtro por tipo de tela.
    @Transactional(readOnly = true)
    public List<HistoryRow> findHistory(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long warehouseId,
            String fabricType,
            boolean closedOnly
    ) {

        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "El rango de fechas es inválido"
            );
        }

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

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("from", from, Types.DATE)
                        .addValue("to", to, Types.DATE);

        StringBuilder filters = new StringBuilder();

        if (productId != null) {
            filters.append(
                    " AND pv.product_id = :productId "
            );

            params.addValue(
                    "productId",
                    productId,
                    Types.BIGINT
            );
        }

        if (warehouseId != null) {
            filters.append(
                    " AND f.warehouse_id = :warehouseId "
            );

            params.addValue(
                    "warehouseId",
                    warehouseId,
                    Types.BIGINT
            );
        }

        if (fabricType != null && !fabricType.isBlank()) {
            filters.append(
                    " AND p.fabric_type = :fabricType "
            );

            params.addValue(
                    "fabricType",
                    fabricType.trim()
            );
        }

        if (closedOnly) {
            filters.append(
                    " AND c.status = 'CLOSED' "
            );
        }

        String sql = """
                SELECT
                    f.snapshot_date,
                    c.status,

                    COALESCE(
                        SUM(f.available_quantity), 0
                    ) AS available,

                    COALESCE(
                        SUM(f.reserved_quantity), 0
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

                INNER JOIN public.product p
                    ON p.id = pv.product_id

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
