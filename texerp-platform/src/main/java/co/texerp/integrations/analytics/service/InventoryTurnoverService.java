
package co.texerp.integrations.analytics.service;

import co.texerp.integrations.analytics.dto.InventoryTurnoverResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryTurnoverService {

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public InventoryTurnoverResponse getTurnover(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long warehouseId
    ) {
        return getTurnover(from, to, productId, warehouseId, null);
    }

    @Transactional(readOnly = true)
    public InventoryTurnoverResponse getTurnover(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long warehouseId,
            String fabricType
    ) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException(
                    "Se requieren dos fechas diferentes y ordenadas"
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

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from, Types.DATE)
                .addValue("to", to, Types.DATE);

        StringBuilder inventoryFilters = new StringBuilder();
        StringBuilder salesFilters = new StringBuilder();

        if (productId != null) {
            params.addValue("productId", productId, Types.BIGINT);
            inventoryFilters.append(" AND pv.product_id = :productId ");
            salesFilters.append(" AND dp.product_id = :productId ");
        }

        if (warehouseId != null) {
            params.addValue("warehouseId", warehouseId, Types.BIGINT);
            inventoryFilters.append(" AND f.warehouse_id = :warehouseId ");
            salesFilters.append(" AND dw.warehouse_id = :warehouseId ");
        }

        if (fabricType != null && !fabricType.isBlank()) {
            params.addValue("fabricType", fabricType.trim());

            inventoryFilters.append(
                    " AND p.fabric_type = :fabricType "
            );

            // Se filtra usando el catálogo de productos.
            salesFilters.append(
                    " AND ps.fabric_type = :fabricType "
            );
        }

        String inventorySql = """
                SELECT
                    f.snapshot_date,
                    COUNT(*) AS snapshot_count,
                    COUNT(*) FILTER (
                        WHERE f.unit_cost_snapshot IS NULL
                    ) AS missing_cost_count,
                    SUM(f.available_inventory_value) AS inventory_value
                FROM texerp_dw.fact_inventory f
                JOIN texerp_dw.inventory_snapshot_closure c
                    ON c.snapshot_date = f.snapshot_date
                JOIN public.product_variant pv
                    ON pv.id = f.variant_id
                JOIN public.product p
                    ON p.id = pv.product_id
                WHERE f.snapshot_date IN (:from, :to)
                  AND c.status = 'CLOSED'
                """
                + inventoryFilters
                + """
                GROUP BY f.snapshot_date
                ORDER BY f.snapshot_date
                """;

        List<Snapshot> snapshots = jdbc.query(
                inventorySql,
                params,
                (rs, rowNum) -> new Snapshot(
                        rs.getObject("snapshot_date", LocalDate.class),
                        rs.getBigDecimal("inventory_value"),
                        rs.getLong("snapshot_count"),
                        rs.getLong("missing_cost_count")
                )
        );

        Snapshot initial = snapshots.stream()
                .filter(s -> s.date().equals(from))
                .findFirst()
                .orElse(null);

        Snapshot ending = snapshots.stream()
                .filter(s -> s.date().equals(to))
                .findFirst()
                .orElse(null);

        String salesSql = """
                SELECT
                    COALESCE(
                        SUM(fs.quantity * fs.unit_cost),
                        0
                    ) AS cost_of_goods_sold
                FROM texerp_dw.fact_sales fs
                JOIN texerp_dw.dim_product dp
                    ON dp.id = fs.product_key
                JOIN texerp_dw.dim_warehouse dw
                    ON dw.id = fs.warehouse_key
                JOIN public.product ps
                    ON ps.id = dp.product_id
                WHERE fs.confirmed_at >=
                    (CAST(:from AS DATE) + INTERVAL '1 day')
                  AND fs.confirmed_at <
                    (CAST(:to AS DATE) + INTERVAL '1 day')
                  AND fs.sale_status NOT IN ('CANCELLED')
                """
                + salesFilters;

        BigDecimal costOfGoodsSold = jdbc.queryForObject(
                salesSql,
                params,
                BigDecimal.class
        );

        if (costOfGoodsSold == null) {
            costOfGoodsSold = BigDecimal.ZERO;
        }

        boolean hasData = initial != null || ending != null;

        if (initial == null || ending == null) {
            return response(
                    from, to, productId, warehouseId,
                    hasData, false, "MISSING_SNAPSHOTS",
                    costOfGoodsSold, initial, ending, null, null
            );
        }

        if (initial.missingCostCount() > 0
                || ending.missingCostCount() > 0
                || initial.value() == null
                || ending.value() == null) {
            return response(
                    from, to, productId, warehouseId,
                    true, false, "INCOMPLETE_COSTS",
                    costOfGoodsSold, initial, ending, null, null
            );
        }

        BigDecimal averageInventory = initial.value()
                .add(ending.value())
                .divide(
                        BigDecimal.valueOf(2),
                        4,
                        RoundingMode.HALF_UP
                );

        if (averageInventory.compareTo(BigDecimal.ZERO) <= 0) {
            return response(
                    from, to, productId, warehouseId,
                    true, false, "INVALID_AVERAGE_INVENTORY",
                    costOfGoodsSold, initial, ending,
                    averageInventory, null
            );
        }

        BigDecimal turnover = costOfGoodsSold.divide(
                averageInventory,
                4,
                RoundingMode.HALF_UP
        );

        return response(
                from, to, productId, warehouseId,
                true, true, "OK",
                costOfGoodsSold, initial, ending,
                averageInventory, turnover
        );
    }

    private InventoryTurnoverResponse response(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long warehouseId,
            boolean hasData,
            boolean valid,
            String status,
            BigDecimal costOfGoodsSold,
            Snapshot initial,
            Snapshot ending,
            BigDecimal average,
            BigDecimal turnover
    ) {
        return new InventoryTurnoverResponse(
                from,
                to,
                productId,
                warehouseId,
                hasData,
                valid,
                status,
                costOfGoodsSold,
                initial == null ? null : initial.value(),
                ending == null ? null : ending.value(),
                average,
                turnover,
                initial == null ? 0L : initial.count(),
                ending == null ? 0L : ending.count()
        );
    }

    private record Snapshot(
            LocalDate date,
            BigDecimal value,
            long count,
            long missingCostCount
    ) {}
}
