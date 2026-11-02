
package co.texerp.integrations.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class EtlSalesDimensionService {

    private final JdbcTemplate jdbcTemplate;

    public EtlSalesDimensionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void loadDimensions() {
        OffsetDateTime versionTime = OffsetDateTime.now();

        loadCustomers(versionTime);
        loadProducts(versionTime);
        loadWarehouses(versionTime);
    }

    private void loadCustomers(OffsetDateTime versionTime) {
        loadScd2(
                """
                SELECT
                    c.id AS business_id,
                    c.document,
                    c.name,
                    c.type,
                    c.classification,
                    c.active
                FROM public.customer c
                """,
                "dim_customer",
                "customer_id",
                "document, name, type, classification, active",
                versionTime
        );
    }

    private void loadProducts(OffsetDateTime versionTime) {
        loadScd2(
                """
                SELECT
                    pv.id AS business_id,
                    pv.product_id,
                    p.code AS product_code,
                    p.name AS product_name,
                    pv.sku,
                    pv.color,
                    pv.pattern,
                    p.fabric_type,
                    pv.sale_price,
                    (p.active AND pv.active) AS active
                FROM public.product_variant pv
                JOIN public.product p
                    ON p.id = pv.product_id
                """,
                "dim_product",
                "variant_id",
                """
                product_id, product_code, product_name,
                sku, color, pattern, fabric_type,
                sale_price, active
                """.replace("\n", " ").trim(),
                versionTime
        );
    }

    private void loadWarehouses(OffsetDateTime versionTime) {
        loadScd2(
                """
                SELECT
                    w.id AS business_id,
                    w.code,
                    w.name,
                    w.location,
                    w.active
                FROM public.warehouse w
                """,
                "dim_warehouse",
                "warehouse_id",
                "code, name, location, active",
                versionTime
        );
    }

    private void loadScd2(
            String sourceQuery,
            String dimensionTable,
            String businessKey,
            String attributes,
            OffsetDateTime versionTime
    ) {
        String[] columns = Arrays.stream(attributes.split(","))
                .map(String::trim)
                .toArray(String[]::new);

        String comparison = Arrays.stream(columns)
                .map(column ->
                        "d." + column
                                + " IS DISTINCT FROM s." + column
                )
                .collect(Collectors.joining(" OR "));

        String source = """
                (%s) s
                """.formatted(sourceQuery);

        // 1. Cerrar versiones actuales que cambiaron.
        String closeSql = """
                UPDATE texerp_dw.%s d
                SET valid_to = ?,
                    is_current = FALSE
                FROM %s
                WHERE d.%s = s.business_id
                  AND d.is_current = TRUE
                  AND (%s)
                """.formatted(
                dimensionTable,
                source,
                businessKey,
                comparison
        );

        jdbcTemplate.update(closeSql, versionTime);

        // 2. Insertar nuevas versiones solo cuando
        //    no exista una versión actual.
        String insertSql = """
                INSERT INTO texerp_dw.%s (
                    %s,
                    %s,
                    valid_from,
                    valid_to,
                    is_current
                )
                SELECT
                    s.business_id,
                    %s,
                    ?,
                    NULL,
                    TRUE
                FROM %s
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM texerp_dw.%s d
                    WHERE d.%s = s.business_id
                      AND d.is_current = TRUE
                )
                """.formatted(
                dimensionTable,
                businessKey,
                attributes,
                Arrays.stream(columns)
                        .map(column -> "s." + column)
                        .collect(Collectors.joining(", ")),
                source,
                dimensionTable,
                businessKey
        );

        jdbcTemplate.update(insertSql, versionTime);
    }
}
