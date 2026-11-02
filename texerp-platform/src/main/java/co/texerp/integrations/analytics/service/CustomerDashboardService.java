package co.texerp.integrations.analytics.service;

import co.texerp.integrations.analytics.dto.CustomerDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerDashboardService {
    private final NamedParameterJdbcTemplate jdbc;
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final String CTE = """
            WITH filtered AS (
                SELECT f.sale_id, f.confirmed_at, f.total, f.quantity, f.unit_cost,
                       dc.customer_id, dc.name, dc.type, dc.classification, dc.active
                FROM texerp_dw.fact_sales f
                JOIN texerp_dw.dim_customer dc ON dc.id = f.customer_key
                WHERE f.sale_status IN ('CONFIRMED','DISPATCHED')
                  AND f.confirmed_at >= :startAt AND f.confirmed_at < :endAt
                  AND (:type IS NULL OR dc.type = :type)
                  AND (:segment IS NULL OR dc.classification = :segment)
                  AND (:productId IS NULL OR EXISTS (
                      SELECT 1 FROM texerp_dw.dim_product dp
                      WHERE dp.id = f.product_key AND dp.product_id = :productId))
            ), by_customer AS (
                SELECT customer_id, MAX(name) AS name, MAX(type) AS type,
                       MAX(classification) AS segment, BOOL_OR(active) AS active,
                       COUNT(DISTINCT sale_id) AS purchase_count,
                       COUNT(DISTINCT (confirmed_at AT TIME ZONE 'America/Bogota')::date) AS purchase_days,
                       COALESCE(SUM(total),0) AS sales,
                       CASE WHEN COUNT(DISTINCT sale_id)>0
                            THEN ROUND(SUM(total)/COUNT(DISTINCT sale_id),2) END AS ticket,
                       CASE WHEN COUNT(*) FILTER (WHERE unit_cost IS NULL)>0 THEN NULL
                            ELSE SUM(total)-SUM(quantity*unit_cost) END AS profit,
                       COUNT(*) FILTER (WHERE unit_cost IS NULL)=0 AS valuation_complete,
                       MAX(confirmed_at) AS last_purchase_at
                FROM filtered GROUP BY customer_id
            )
            """;

    @Transactional(readOnly = true)
    public CustomerDashboardResponse getDashboard(LocalDate from, LocalDate to,
                                                  String type, String segment, Long productId, int limit, String rankingBy) {
        if (from == null || to == null || from.isAfter(to))
            throw new IllegalArgumentException("Rango de fechas inválido");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit debe estar entre 1 y 100");
        if (productId != null && productId <= 0) throw new IllegalArgumentException("productId inválido");
        String order = switch (rankingBy == null ? "SALES" : rankingBy.toUpperCase()) {
            case "SALES" -> "sales DESC";
            case "PROFIT" -> "profit DESC NULLS LAST";
            case "FREQUENCY" -> "purchase_count DESC";
            default -> throw new IllegalArgumentException("rankingBy: SALES, PROFIT o FREQUENCY");
        };
        var params = new MapSqlParameterSource()
                .addValue("startAt", from.atStartOfDay(ZONE).toOffsetDateTime())
                .addValue("endAt", to.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime())
                .addValue("type", blankToNull(type)).addValue("segment", blankToNull(segment))
                .addValue("productId", productId).addValue("limit", limit);
        var summary = jdbc.queryForObject(CTE + """
                SELECT COUNT(*) AS customers,
                       COALESCE(SUM(purchase_count),0) AS transactions,
                       COALESCE(SUM(sales),0) AS sales,
                       CASE WHEN SUM(purchase_count)>0
                            THEN ROUND(SUM(sales)/SUM(purchase_count),2) ELSE 0 END AS ticket,
                       COUNT(*) FILTER (WHERE purchase_days>=2) AS repeat_customers,
                       CASE WHEN COUNT(*)>0 THEN
                            ROUND(100.0*COUNT(*) FILTER (WHERE purchase_days>=2)/COUNT(*),2)
                            ELSE 0 END AS repeat_percentage
                FROM by_customer
                """, params, (rs, n) -> new CustomerDashboardResponse.Summary(
                rs.getLong("customers"), rs.getLong("transactions"), rs.getBigDecimal("sales"),
                rs.getBigDecimal("ticket"), rs.getLong("repeat_customers"),
                rs.getBigDecimal("repeat_percentage")));
        List<CustomerDashboardResponse.CustomerRanking> ranking = jdbc.query(CTE + """
                SELECT * FROM by_customer ORDER BY
                """ + order + ", customer_id LIMIT :limit", params, (rs, n) ->
                new CustomerDashboardResponse.CustomerRanking(rs.getLong("customer_id"),
                        rs.getString("name"), rs.getString("type"), rs.getString("segment"),
                        rs.getBoolean("active"), rs.getLong("purchase_count"),
                        rs.getLong("purchase_days"), rs.getBigDecimal("sales"), rs.getBigDecimal("ticket"),
                        rs.getBigDecimal("profit"), rs.getBoolean("valuation_complete"),
                        rs.getObject("last_purchase_at", java.time.OffsetDateTime.class),
                        rs.getLong("purchase_days") >= 2));
        return new CustomerDashboardResponse(from, to, summary != null && summary.transactions() > 0,
                summary, ranking);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
