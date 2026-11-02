
package co.texerp.integrations.analytics.service;

import co.texerp.integrations.analytics.dto.PurchaseDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PurchaseDashboardService {

    private final NamedParameterJdbcTemplate jdbc;

    private static final ZoneId ZONE =
            ZoneId.of("America/Bogota");

    private static final String BASE = """
            FROM texerp_dw.fact_purchases f
            JOIN texerp_dw.dim_supplier s
              ON s.supplier_id = f.supplier_id
            WHERE f.order_status IN (
                'APPROVED',
                'PARTIALLY_RECEIVED',
                'RECEIVED'
            )
              AND f.ordered_at >= :startAt
              AND f.ordered_at < :endAt

              AND (
                  CAST(:supplierId AS bigint) IS NULL
                  OR f.supplier_id = CAST(:supplierId AS bigint)
              )

              AND (
                  CAST(:productId AS bigint) IS NULL
                  OR EXISTS (
                      SELECT 1
                      FROM texerp_dw.dim_product dp
                      WHERE dp.variant_id = f.product_variant_id
                        AND dp.is_current = true
                        AND dp.product_id = CAST(:productId AS bigint)
                  )
              )

              AND (
                  CAST(:warehouseId AS bigint) IS NULL
                  OR EXISTS (
                      SELECT 1
                      FROM public.purchase_receipt_line prl
                      JOIN public.purchase_receipt pr
                        ON pr.id = prl.receipt_id
                      WHERE prl.purchase_order_line_id =
                            f.purchase_order_line_id
                        AND pr.warehouse_id =
                            CAST(:warehouseId AS bigint)
                  )
              )
            """;

    private static final String AGGREGATE = """
            COALESCE(SUM(f.ordered_amount), 0)
                AS purchased_amount,

            COALESCE(SUM(f.ordered_quantity), 0)
                AS ordered_quantity,

            COALESCE(SUM(f.received_quantity), 0)
                AS received_quantity,

            COALESCE(
                SUM(f.ordered_quantity - f.received_quantity),
                0
            ) AS pending_quantity,

            CASE
                WHEN SUM(f.ordered_quantity) > 0
                THEN ROUND(
                    100 * SUM(f.received_quantity)
                    / SUM(f.ordered_quantity),
                    2
                )
                ELSE 0
            END AS fulfillment,

            ROUND(
                AVG(
                    EXTRACT(
                        EPOCH FROM (
                            f.first_received_at - f.ordered_at
                        )
                    ) / 86400
                ) FILTER (
                    WHERE f.first_received_at IS NOT NULL
                ),
                2
            ) AS avg_delivery_days,

            COUNT(DISTINCT f.purchase_order_id)
                AS order_count
            """;

    @Transactional(readOnly = true)
    public PurchaseDashboardResponse getDashboard(
            LocalDate from,
            LocalDate to,
            Long supplierId,
            Long productId,
            Long warehouseId,
            int limit,
            String rankingBy
    ) {

        // 1. Validación de fechas
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Rango de fechas inválido"
            );
        }

        // 2. Validación del límite
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "limit debe estar entre 1 y 100"
            );
        }

        // 3. Validación de identificadores
        if ((supplierId != null && supplierId <= 0)
                || (productId != null && productId <= 0)
                || (warehouseId != null && warehouseId <= 0)) {

            throw new IllegalArgumentException(
                    "Los IDs deben ser positivos"
            );
        }

        // 4. Criterio de ordenamiento
        String rankingCriteria = rankingBy == null
                ? "AMOUNT"
                : rankingBy.trim().toUpperCase(Locale.ROOT);

        String order = switch (rankingCriteria) {
            case "AMOUNT" ->
                    "purchased_amount DESC";

            case "PENDING" ->
                    "pending_quantity DESC";

            case "DELIVERY" ->
                    "avg_delivery_days ASC NULLS LAST";

            case "COST" ->
                    "avg_unit_cost DESC NULLS LAST";

            default -> throw new IllegalArgumentException(
                    "rankingBy debe ser AMOUNT, PENDING, DELIVERY o COST"
            );
        };

        // 5. Parámetros de consulta
        var params = new MapSqlParameterSource()
                .addValue(
                        "startAt",
                        from.atStartOfDay(ZONE).toOffsetDateTime()
                )
                .addValue(
                        "endAt",
                        to.plusDays(1)
                                .atStartOfDay(ZONE)
                                .toOffsetDateTime()
                )
                .addValue("supplierId", supplierId)
                .addValue("productId", productId)
                .addValue("warehouseId", warehouseId)
                .addValue("limit", limit);

        // 6. Resumen general
        String summarySql = """
                SELECT
                """
                + AGGREGATE
                + """
                ,
                COUNT(DISTINCT f.supplier_id) AS supplier_count
                """
                + BASE;

        PurchaseDashboardResponse.Summary summary =
                jdbc.queryForObject(
                        summarySql,
                        params,
                        (rs, rowNum) ->
                                new PurchaseDashboardResponse.Summary(
                                        rs.getBigDecimal("purchased_amount"),
                                        rs.getBigDecimal("ordered_quantity"),
                                        rs.getBigDecimal("received_quantity"),
                                        rs.getBigDecimal("pending_quantity"),
                                        rs.getBigDecimal("fulfillment"),
                                        rs.getBigDecimal("avg_delivery_days"),
                                        rs.getLong("order_count"),
                                        rs.getLong("supplier_count")
                                )
                );

        // 7. Ranking de proveedores
        String rankingSql = """
                SELECT
                    f.supplier_id,
                    MAX(s.name) AS supplier_name,
                """
                + AGGREGATE
                + """
                ,
                CASE
                    WHEN SUM(f.ordered_quantity) > 0
                    THEN ROUND(
                        SUM(f.ordered_amount)
                        / SUM(f.ordered_quantity),
                        2
                    )
                    ELSE NULL
                END AS avg_unit_cost
                """
                + BASE
                + """
                GROUP BY f.supplier_id
                ORDER BY
                """
                + order
                + """
                , f.supplier_id
                LIMIT :limit
                """;

        List<PurchaseDashboardResponse.SupplierRanking> ranking =
                jdbc.query(
                        rankingSql,
                        params,
                        (rs, rowNum) ->
                                new PurchaseDashboardResponse.SupplierRanking(
                                        rs.getLong("supplier_id"),
                                        rs.getString("supplier_name"),
                                        rs.getBigDecimal("purchased_amount"),
                                        rs.getBigDecimal("ordered_quantity"),
                                        rs.getBigDecimal("received_quantity"),
                                        rs.getBigDecimal("pending_quantity"),
                                        rs.getBigDecimal("fulfillment"),
                                        rs.getBigDecimal("avg_unit_cost"),
                                        rs.getBigDecimal("avg_delivery_days"),
                                        rs.getLong("order_count")
                                )
                );

        // 8. Construcción de respuesta
        boolean hasData = summary != null
                && summary.purchaseOrderCount() > 0;

        return new PurchaseDashboardResponse(
                from,
                to,
                hasData,
                summary,
                ranking
        );
    }
}
