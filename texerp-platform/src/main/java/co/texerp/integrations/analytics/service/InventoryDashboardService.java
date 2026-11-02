
package co.texerp.integrations.analytics.service;

import co.texerp.integrations.analytics.dto.InventoryDashboardResponse;
import co.texerp.integrations.analytics.dto.InventoryDashboardResponse.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryDashboardService {

    private static final ZoneId ZONE =
            ZoneId.of("America/Bogota");

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public InventoryDashboardResponse getDashboard(
            LocalDate from,
            LocalDate to,
            Long warehouseId,
            Long productId,
            String fabricType,
            int inactiveDays,
            int limit
    ) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "El rango de fechas es inválido"
            );
        }

        if (warehouseId != null && warehouseId <= 0) {
            throw new IllegalArgumentException(
                    "warehouseId debe ser positivo"
            );
        }

        if (productId != null && productId <= 0) {
            throw new IllegalArgumentException(
                    "productId debe ser positivo"
            );
        }

        if (inactiveDays < 1 || inactiveDays > 3650) {
            throw new IllegalArgumentException(
                    "inactiveDays debe estar entre 1 y 3650"
            );
        }

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "limit debe estar entre 1 y 100"
            );
        }

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(
                                "from",
                                from.atStartOfDay(ZONE)
                                        .toOffsetDateTime()
                        )
                        .addValue(
                                "toExclusive",
                                to.plusDays(1)
                                        .atStartOfDay(ZONE)
                                        .toOffsetDateTime()
                        )
                        .addValue("limit", limit)
                        .addValue(
                                "inactiveCutoff",
                                to.plusDays(1L - inactiveDays)
                                        .atStartOfDay(ZONE)
                                        .toOffsetDateTime()
                        )
                        .addValue(
                                "endOfPeriod",
                                to.plusDays(1)
                                        .atStartOfDay(ZONE)
                                        .toOffsetDateTime()
                        )
                        .addValue("asOfDate", to);

        String filters = filters(
                params, warehouseId, productId, fabricType
        );

        Summary summary = getSummary(params, filters);
        List<WarehouseStock> warehouses =
                getWarehouses(params, filters);
        List<CriticalStock> critical =
                getCriticalStock(params, filters);
        List<MovementSummary> movements =
                getMovements(params, filters);
        List<InactiveProduct> inactive =
                getInactiveProducts(params, filters);

        boolean hasData =
                summary.skuWarehouseCount() > 0
                        || movements.stream()
                        .anyMatch(m -> m.movements() > 0);

        return new InventoryDashboardResponse(
                from,
                to,
                hasData,
                false,
                summary,
                warehouses,
                critical,
                movements,
                inactive
        );
    }

    private String filters(
            MapSqlParameterSource params,
            Long warehouseId,
            Long productId,
            String fabricType
    ) {
        StringBuilder sql = new StringBuilder();

        if (warehouseId != null) {
            sql.append(" AND w.id = :warehouseId ");
            params.addValue("warehouseId", warehouseId);
        }

        if (productId != null) {
            sql.append(" AND p.id = :productId ");
            params.addValue("productId", productId);
        }

        if (fabricType != null
                && !fabricType.isBlank()) {
            sql.append(
                    " AND p.fabric_type = :fabricType "
            );
            params.addValue(
                    "fabricType", fabricType.trim()
            );
        }

        return sql.toString();
    }

    private String balanceFrom() {
        return """
            FROM public.inventory_balance ib
            JOIN public.product_variant pv
              ON pv.id = ib.variant_id
            JOIN public.product p
              ON p.id = pv.product_id
            JOIN public.warehouse w
              ON w.id = ib.warehouse_id
            WHERE 1 = 1
            """;
    }

    private String movementFrom() {
        return """
            FROM public.inventory_movement im
            JOIN public.product_variant pv
              ON pv.id = im.variant_id
            JOIN public.product p
              ON p.id = pv.product_id
            JOIN public.warehouse w
              ON w.id = im.warehouse_id
            WHERE 1 = 1
            """;
    }

    private Summary getSummary(
            MapSqlParameterSource params,
            String filters
    ) {
        String sql = """
            SELECT
                COALESCE(
                    SUM(ib.available_quantity), 0
                ) AS available,
                COALESCE(
                    SUM(ib.reserved_quantity), 0
                ) AS reserved,
                COALESCE(
                    SUM(
                        ib.available_quantity
                        * COALESCE(pv.cost, 0)
                    ), 0
                ) AS inventory_value,
                COUNT(*) AS sku_warehouse_count,
                COUNT(*) FILTER (
                    WHERE ib.available_quantity
                          < ib.minimum_quantity
                ) AS below_minimum_count,
                COALESCE(
                    SUM(
                        GREATEST(
                            ib.minimum_quantity
                            - ib.available_quantity,
                            0
                        )
                    ), 0
                ) AS below_minimum_units
            """
                + balanceFrom()
                + filters;

        return jdbc.queryForObject(
                sql,
                params,
                (rs, rowNum) -> {
                    BigDecimal available =
                            rs.getBigDecimal("available");
                    BigDecimal reserved =
                            rs.getBigDecimal("reserved");
                    BigDecimal value =
                            rs.getBigDecimal("inventory_value");
                    long count =
                            rs.getLong("sku_warehouse_count");
                    long below =
                            rs.getLong("below_minimum_count");
                    BigDecimal shortage =
                            rs.getBigDecimal("below_minimum_units");

                    MovementTotals totals =
                            getMovementTotals(params, filters);

                    return new Summary(
                            available,
                            reserved,
                            value,
                            count,
                            below,
                            shortage,
                            totals.entries(),
                            totals.exits(),
                            totals.adjustments(),
                            totals.net()
                    );
                }
        );
    }

    private record MovementTotals(
            BigDecimal entries,
            BigDecimal exits,
            BigDecimal adjustments,
            BigDecimal net
    ) {}

    private MovementTotals getMovementTotals(
            MapSqlParameterSource params,
            String filters
    ) {
        String sql = """
            SELECT
                COALESCE(SUM(
                    CASE
                        WHEN im.movement_type <> 'ADJUSTMENT'
                             AND im.quantity > 0
                        THEN im.quantity
                        ELSE 0
                    END
                ), 0) AS entries,
                COALESCE(SUM(
                    CASE
                        WHEN im.movement_type <> 'ADJUSTMENT'
                             AND im.quantity < 0
                        THEN ABS(im.quantity)
                        ELSE 0
                    END
                ), 0) AS exits,
                COALESCE(SUM(
                    CASE
                        WHEN im.movement_type = 'ADJUSTMENT'
                        THEN im.quantity
                        ELSE 0
                    END
                ), 0) AS adjustments,
                COALESCE(SUM(im.quantity), 0) AS net
            """
                + movementFrom()
                + filters
                + """
                    AND im.movement_at >= :from
                    AND im.movement_at < :toExclusive
                    """;

        return jdbc.queryForObject(
                sql,
                params,
                (rs, rowNum) -> new MovementTotals(
                        rs.getBigDecimal("entries"),
                        rs.getBigDecimal("exits"),
                        rs.getBigDecimal("adjustments"),
                        rs.getBigDecimal("net")
                )
        );
    }

    private List<WarehouseStock> getWarehouses(
            MapSqlParameterSource params,
            String filters
    ) {
        String sql = """
            SELECT
                w.id AS warehouse_id,
                w.code AS warehouse_code,
                w.name AS warehouse_name,
                COALESCE(
                    SUM(ib.available_quantity), 0
                ) AS available,
                COALESCE(
                    SUM(ib.reserved_quantity), 0
                ) AS reserved,
                COALESCE(
                    SUM(
                        ib.available_quantity
                        * COALESCE(pv.cost, 0)
                    ), 0
                ) AS inventory_value,
                COUNT(*) FILTER (
                    WHERE ib.available_quantity
                          < ib.minimum_quantity
                ) AS below_minimum
            """
                + balanceFrom()
                + filters
                + """
                    GROUP BY w.id, w.code, w.name
                    ORDER BY inventory_value DESC
                    """;

        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new WarehouseStock(
                        rs.getLong("warehouse_id"),
                        rs.getString("warehouse_code"),
                        rs.getString("warehouse_name"),
                        rs.getBigDecimal("available"),
                        rs.getBigDecimal("reserved"),
                        rs.getBigDecimal("inventory_value"),
                        rs.getLong("below_minimum")
                )
        );
    }

    private List<CriticalStock> getCriticalStock(
            MapSqlParameterSource params,
            String filters
    ) {
        String sql = """
            SELECT
                pv.id AS variant_id,
                pv.sku,
                p.name AS product_name,
                w.id AS warehouse_id,
                w.name AS warehouse_name,
                ib.available_quantity,
                ib.reserved_quantity,
                ib.minimum_quantity,
                (
                    ib.minimum_quantity
                    - ib.available_quantity
                ) AS shortage
            """
                + balanceFrom()
                + filters
                + """
                    AND ib.available_quantity
                        < ib.minimum_quantity
                    ORDER BY shortage DESC, pv.sku
                    LIMIT :limit
                    """;

        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new CriticalStock(
                        rs.getLong("variant_id"),
                        rs.getString("sku"),
                        rs.getString("product_name"),
                        rs.getLong("warehouse_id"),
                        rs.getString("warehouse_name"),
                        rs.getBigDecimal("available_quantity"),
                        rs.getBigDecimal("reserved_quantity"),
                        rs.getBigDecimal("minimum_quantity"),
                        rs.getBigDecimal("shortage")
                )
        );
    }

    private List<MovementSummary> getMovements(
            MapSqlParameterSource params,
            String filters
    ) {
        String sql = """
            SELECT
                im.movement_type,
                COUNT(*) AS movements,
                COALESCE(SUM(
                    GREATEST(im.quantity, 0)
                ), 0) AS positive_quantity,
                COALESCE(SUM(
                    ABS(LEAST(im.quantity, 0))
                ), 0) AS negative_quantity,
                COALESCE(
                    SUM(im.quantity), 0
                ) AS net_quantity,
                COALESCE(
                    SUM(im.reserved_delta), 0
                ) AS reserved_delta
            """
                + movementFrom()
                + filters
                + """
                    AND im.movement_at >= :from
                    AND im.movement_at < :toExclusive
                    GROUP BY im.movement_type
                    ORDER BY im.movement_type
                    """;

        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new MovementSummary(
                        rs.getString("movement_type"),
                        rs.getLong("movements"),
                        rs.getBigDecimal("positive_quantity"),
                        rs.getBigDecimal("negative_quantity"),
                        rs.getBigDecimal("net_quantity"),
                        rs.getBigDecimal("reserved_delta")
                )
        );
    }

    private List<InactiveProduct> getInactiveProducts(
            MapSqlParameterSource params,
            String filters
    ) {
        String sql = """
            SELECT
                pv.id AS variant_id,
                pv.sku,
                p.name AS product_name,
                w.id AS warehouse_id,
                w.name AS warehouse_name,
                ib.available_quantity,
                CAST(
                    MAX(im.movement_at AT TIME ZONE
                        'America/Bogota') AS date
                ) AS last_movement_date,
                CASE
                    WHEN MAX(im.movement_at) IS NULL
                    THEN NULL
                    ELSE (
                        :asOfDate
                        - CAST(
                            MAX(im.movement_at AT TIME ZONE
                                'America/Bogota') AS date
                        )
                    )
                END AS days_without_movement
            """
                + balanceFrom()
                + filters
                + """
                    AND ib.available_quantity > 0
                    LEFT_JOIN_PLACEHOLDER
                    """;

        // El LEFT JOIN debe ir antes del WHERE.
        // Construimos la consulta completa para mantener
        // los filtros aplicados al balance.
        sql = """
            SELECT
                pv.id AS variant_id,
                pv.sku,
                p.name AS product_name,
                w.id AS warehouse_id,
                w.name AS warehouse_name,
                ib.available_quantity,
                CAST(
                    MAX(im.movement_at AT TIME ZONE
                        'America/Bogota') AS date
                ) AS last_movement_date,
                CASE
                    WHEN MAX(im.movement_at) IS NULL
                    THEN NULL
                    ELSE (
                        :asOfDate
                        - CAST(
                            MAX(im.movement_at AT TIME ZONE
                                'America/Bogota') AS date
                        )
                    )
                END AS days_without_movement
            FROM public.inventory_balance ib
            JOIN public.product_variant pv
              ON pv.id = ib.variant_id
            JOIN public.product p
              ON p.id = pv.product_id
            JOIN public.warehouse w
              ON w.id = ib.warehouse_id
            LEFT JOIN public.inventory_movement im
              ON im.variant_id = ib.variant_id
             AND im.warehouse_id = ib.warehouse_id
             AND im.movement_at < :endOfPeriod
            WHERE ib.available_quantity > 0
            """
                + filters
                + """
                    GROUP BY
                        pv.id,
                        pv.sku,
                        p.name,
                        w.id,
                        w.name,
                        ib.available_quantity
                    HAVING
                        MAX(im.movement_at) IS NULL
                        OR MAX(im.movement_at)
                            < :inactiveCutoff
                    ORDER BY
                        last_movement_date ASC NULLS FIRST,
                        ib.available_quantity DESC
                    LIMIT :limit
                    """;

        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> {
                    java.sql.Date date =
                            rs.getDate("last_movement_date");

                    Object daysObject =
                            rs.getObject("days_without_movement");

                    return new InactiveProduct(
                            rs.getLong("variant_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            rs.getLong("warehouse_id"),
                            rs.getString("warehouse_name"),
                            rs.getBigDecimal("available_quantity"),
                            date == null
                                    ? null
                                    : date.toLocalDate(),
                            daysObject == null
                                    ? null
                                    : ((Number) daysObject).longValue()
                    );
                }
        );
    }
}
