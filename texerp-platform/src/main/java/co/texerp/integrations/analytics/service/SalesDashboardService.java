
package co.texerp.integrations.analytics.service;

import co.texerp.integrations.analytics.dto.SalesDashboardResponse;
import co.texerp.integrations.analytics.dto.SalesDashboardResponse.*;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SalesDashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("America/Bogota");

    private final NamedParameterJdbcTemplate jdbc;

    public SalesDashboardService(
            NamedParameterJdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SalesDashboardResponse getDashboard(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long customerId,
            Long warehouseId,
            String fabricType,
            int limit,
            String rankingBy
    ) {

        validateFilters(
                from,
                to,
                productId,
                customerId,
                warehouseId,
                limit,
                rankingBy
        );

        MapSqlParameterSource currentParams = params(
                from,
                to,
                productId,
                customerId,
                warehouseId,
                fabricType
        );

        Metrics current = getMetrics(currentParams);

        long days = ChronoUnit.DAYS.between(from, to) + 1;

        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = from.minusDays(days);

        MapSqlParameterSource previousParams = params(
                previousFrom,
                previousTo,
                productId,
                customerId,
                warehouseId,
                fabricType
        );

        Metrics previous = getMetrics(previousParams);

        Comparison comparison = new Comparison(
                previousFrom,
                previousTo,
                previous,
                change(
                        current.totalSales(),
                        previous.totalSales()
                ),
                change(
                        current.profit(),
                        previous.profit()
                ),
                change(
                        current.unitsSold(),
                        previous.unitsSold()
                )
        );

        List<Ranking> products = getRanking(
                currentParams,
                "PRODUCT",
                rankingBy,
                limit
        );

        List<Ranking> customers = getRanking(
                currentParams,
                "CUSTOMER",
                rankingBy,
                limit
        );

        return new SalesDashboardResponse(
                from,
                to,
                current.salesCount() > 0,
                current,
                comparison,
                products,
                customers
        );
    }

    private void validateFilters(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long customerId,
            Long warehouseId,
            int limit,
            String rankingBy
    ) {

        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "El rango de fechas no es válido"
            );
        }

        if (to.equals(LocalDate.MAX)) {
            throw new IllegalArgumentException(
                    "La fecha final está fuera del rango permitido"
            );
        }

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "El límite debe estar entre 1 y 100"
            );
        }

        if ((productId != null && productId <= 0)
                || (customerId != null && customerId <= 0)
                || (warehouseId != null && warehouseId <= 0)) {
            throw new IllegalArgumentException(
                    "Los identificadores deben ser positivos"
            );
        }

        if (rankingBy == null
                || (!"SALES".equalsIgnoreCase(rankingBy)
                && !"PROFIT".equalsIgnoreCase(rankingBy))) {
            throw new IllegalArgumentException(
                    "rankingBy debe ser SALES o PROFIT"
            );
        }
    }

    private MapSqlParameterSource params(
            LocalDate from,
            LocalDate to,
            Long productId,
            Long customerId,
            Long warehouseId,
            String fabricType
    ) {

        String normalizedFabricType =
                fabricType == null || fabricType.isBlank()
                        ? null
                        : fabricType.trim();

        return new MapSqlParameterSource()
                .addValue(
                        "from",
                        from.atStartOfDay(BUSINESS_ZONE)
                                .toOffsetDateTime()
                )
                .addValue(
                        "toExclusive",
                        to.plusDays(1)
                                .atStartOfDay(BUSINESS_ZONE)
                                .toOffsetDateTime()
                )
                .addValue("productId", productId)
                .addValue("customerId", customerId)
                .addValue("warehouseId", warehouseId)
                .addValue("fabricType", normalizedFabricType);
    }

    private String baseFromAndWhere(
            MapSqlParameterSource params
    ) {

        StringBuilder sql = new StringBuilder("""
            FROM texerp_dw.fact_sales f

            INNER JOIN texerp_dw.dim_product dp
                ON dp.id = f.product_key

            INNER JOIN texerp_dw.dim_customer dc
                ON dc.id = f.customer_key

            INNER JOIN texerp_dw.dim_warehouse dw
                ON dw.id = f.warehouse_key

            WHERE f.confirmed_at >= :from
              AND f.confirmed_at < :toExclusive
            """);

        if (params.getValue("productId") != null) {
            sql.append("""
                AND dp.product_id = :productId
                """);
        }

        if (params.getValue("customerId") != null) {
            sql.append("""
                AND dc.customer_id = :customerId
                """);
        }

        if (params.getValue("warehouseId") != null) {
            sql.append("""
                AND dw.warehouse_id = :warehouseId
                """);
        }

        if (params.getValue("fabricType") != null) {
            sql.append("""
                AND dp.fabric_type = :fabricType
                """);
        }

        return sql.toString();
    }

    private Metrics getMetrics(
            MapSqlParameterSource params
    ) {

        String sql = """
            SELECT
                COALESCE(SUM(f.total), 0) AS total_sales,
                COALESCE(SUM(f.quantity), 0) AS units_sold,
                COUNT(DISTINCT f.sale_id) AS sales_count,

                COALESCE(
                    SUM(f.quantity * f.unit_cost),
                    0
                ) AS total_cost

            """ + baseFromAndWhere(params);

        return jdbc.queryForObject(
                sql,
                params,
                (rs, rowNum) -> {

                    BigDecimal sales = nz(
                            rs.getBigDecimal("total_sales")
                    );

                    BigDecimal units = nz(
                            rs.getBigDecimal("units_sold")
                    );

                    long count = rs.getLong("sales_count");

                    BigDecimal cost = nz(
                            rs.getBigDecimal("total_cost")
                    );

                    BigDecimal profit = sales.subtract(cost);

                    BigDecimal averageTicket = count == 0
                            ? ZERO
                            : sales.divide(
                            BigDecimal.valueOf(count),
                            2,
                            RoundingMode.HALF_UP
                    );

                    return new Metrics(
                            sales,
                            units,
                            count,
                            averageTicket,
                            cost,
                            profit,
                            percentage(profit, sales)
                    );
                }
        );
    }

    private List<Ranking> getRanking(
            MapSqlParameterSource params,
            String type,
            String rankingBy,
            int limit
    ) {

        boolean productRanking = "PRODUCT".equals(type);

        if (!productRanking && !"CUSTOMER".equals(type)) {
            throw new IllegalArgumentException(
                    "Tipo de ranking no válido"
            );
        }

        String idColumn = productRanking
                ? "dp.product_id"
                : "dc.customer_id";

        String nameColumn = productRanking
                ? "COALESCE(dp.product_name, 'Sin nombre')"
                : "COALESCE(dc.name, 'Sin nombre')";

        String orderColumn = "PROFIT".equalsIgnoreCase(rankingBy)
                ? "profit"
                : "sales";

        String sql = """
            SELECT
                ranked.entity_id,
                ranked.entity_name,
                ranked.sales,
                ranked.cost,
                ranked.units,
                ranked.sales - ranked.cost AS profit

            FROM (
                SELECT
            """ + idColumn + """
                    AS entity_id,
            """ + nameColumn + """
                    AS entity_name,

                    COALESCE(SUM(f.total), 0) AS sales,

                    COALESCE(
                        SUM(f.quantity * f.unit_cost),
                        0
                    ) AS cost,

                    COALESCE(
                        SUM(f.quantity),
                        0
                    ) AS units

                """ + baseFromAndWhere(params) + """

                GROUP BY
                """ + idColumn + ", " + nameColumn + """

            ) ranked

            ORDER BY
            """ + orderColumn + """
                DESC,
                entity_id ASC

            LIMIT :limit
            """;

        params.addValue("limit", limit);

        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> {

                    BigDecimal sales = nz(
                            rs.getBigDecimal("sales")
                    );

                    BigDecimal cost = nz(
                            rs.getBigDecimal("cost")
                    );

                    BigDecimal profit = sales.subtract(cost);

                    Long id = rs.getObject(
                            "entity_id",
                            Long.class
                    );

                    return new Ranking(
                            id,
                            rs.getString("entity_name"),
                            sales,
                            cost,
                            profit,
                            percentage(profit, sales),
                            nz(rs.getBigDecimal("units"))
                    );
                }
        );
    }

    private Change change(
            BigDecimal current,
            BigDecimal previous
    ) {

        BigDecimal absolute = current.subtract(previous);

        BigDecimal percent = previous.compareTo(ZERO) == 0
                ? null
                : absolute.multiply(HUNDRED)
                .divide(
                        previous.abs(),
                        2,
                        RoundingMode.HALF_UP
                );

        return new Change(
                absolute,
                percent
        );
    }

    private BigDecimal percentage(
            BigDecimal numerator,
            BigDecimal denominator
    ) {

        if (denominator.compareTo(ZERO) == 0) {
            return ZERO;
        }

        return numerator.multiply(HUNDRED)
                .divide(
                        denominator,
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
