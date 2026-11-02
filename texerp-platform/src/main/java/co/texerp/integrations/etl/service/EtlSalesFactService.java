
package co.texerp.integrations.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EtlSalesFactService {

    private final JdbcTemplate jdbcTemplate;

    public EtlSalesFactService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void validateDimensions(Long executionId) {

        String sql = """
            SELECT COUNT(*)
            FROM texerp_dw.stg_sales s

            LEFT JOIN texerp_dw.dim_customer dc
                ON dc.customer_id = s.customer_id
               AND dc.is_current = TRUE

            LEFT JOIN texerp_dw.dim_warehouse dw
                ON dw.warehouse_id = s.warehouse_id
               AND dw.is_current = TRUE

            WHERE s.execution_id = ?

              AND NOT EXISTS (
                  SELECT 1
                  FROM texerp_dw.staging_rejections r
                  WHERE r.execution_id = s.execution_id
                    AND r.source_table IN (
                        'sale_line',
                        'stg_sales'
                    )
                    AND r.source_record_id =
                        s.sale_line_id::TEXT
              )

              AND (
                  dc.id IS NULL
                  OR dw.id IS NULL
              )
            """;

        Long missing = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                executionId
        );

        if (missing != null && missing > 0) {
            throw new IllegalStateException(
                    "Existen " + missing
                            + " líneas de venta sin dimensiones "
                            + "de cliente o bodega válidas"
            );
        }

        Long unknownCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM texerp_dw.dim_product
                WHERE variant_id = -1
                  AND is_current = TRUE
                """,
                Long.class
        );

        if (unknownCount == null || unknownCount != 1) {
            throw new IllegalStateException(
                    "La dimensión de producto Desconocido "
                            + "no está configurada correctamente"
            );
        }
    }

    public int loadFacts(Long executionId) {

        String sql = """
            INSERT INTO texerp_dw.fact_sales (
                sale_line_id,
                sale_id,
                sale_number,
                customer_key,
                product_key,
                warehouse_key,
                sale_status,
                confirmed_at,
                quantity,
                unit_price,
                unit_cost,
                discount_amount,
                tax_amount,
                subtotal,
                total,
                source_updated_at,
                loaded_at
            )
            SELECT
                s.sale_line_id,
                s.sale_id,
                s.sale_number,
                dc.id,
                COALESCE(dp.id, unknown_product.id),
                dw.id,
                s.sale_status,
                s.confirmed_at,
                s.quantity,
                s.unit_price,
                s.unit_cost,
                s.discount_amount,
                s.tax_amount,
                s.subtotal,
                s.total,
                s.source_updated_at,
                NOW()

            FROM texerp_dw.stg_sales s

            INNER JOIN texerp_dw.dim_customer dc
                ON dc.customer_id = s.customer_id
               AND dc.is_current = TRUE

            LEFT JOIN texerp_dw.dim_product dp
                ON dp.variant_id = s.variant_id
               AND dp.is_current = TRUE

            CROSS JOIN texerp_dw.dim_product unknown_product

            INNER JOIN texerp_dw.dim_warehouse dw
                ON dw.warehouse_id = s.warehouse_id
               AND dw.is_current = TRUE

            WHERE s.execution_id = ?

              AND unknown_product.variant_id = -1
              AND unknown_product.is_current = TRUE

              AND NOT EXISTS (
                  SELECT 1
                  FROM texerp_dw.staging_rejections r
                  WHERE r.execution_id = s.execution_id
                    AND r.source_table IN (
                        'sale_line',
                        'stg_sales'
                    )
                    AND r.source_record_id =
                        s.sale_line_id::TEXT
              )

            ON CONFLICT (sale_line_id)
            DO UPDATE SET

                sale_id = EXCLUDED.sale_id,
                sale_number = EXCLUDED.sale_number,

                customer_key = CASE
                    WHEN (
                        SELECT old_c.customer_id
                        FROM texerp_dw.dim_customer old_c
                        WHERE old_c.id = fact_sales.customer_key
                    ) = (
                        SELECT new_c.customer_id
                        FROM texerp_dw.dim_customer new_c
                        WHERE new_c.id = EXCLUDED.customer_key
                    )
                    THEN fact_sales.customer_key
                    ELSE EXCLUDED.customer_key
                END,

                product_key = CASE
                    WHEN (
                        SELECT old_p.variant_id
                        FROM texerp_dw.dim_product old_p
                        WHERE old_p.id = fact_sales.product_key
                    ) = (
                        SELECT new_p.variant_id
                        FROM texerp_dw.dim_product new_p
                        WHERE new_p.id = EXCLUDED.product_key
                    )
                    THEN fact_sales.product_key
                    ELSE EXCLUDED.product_key
                END,

                warehouse_key = CASE
                    WHEN (
                        SELECT old_w.warehouse_id
                        FROM texerp_dw.dim_warehouse old_w
                        WHERE old_w.id = fact_sales.warehouse_key
                    ) = (
                        SELECT new_w.warehouse_id
                        FROM texerp_dw.dim_warehouse new_w
                        WHERE new_w.id = EXCLUDED.warehouse_key
                    )
                    THEN fact_sales.warehouse_key
                    ELSE EXCLUDED.warehouse_key
                END,

                sale_status = EXCLUDED.sale_status,
                confirmed_at = EXCLUDED.confirmed_at,
                quantity = EXCLUDED.quantity,
                unit_price = EXCLUDED.unit_price,
                unit_cost = EXCLUDED.unit_cost,
                discount_amount = EXCLUDED.discount_amount,
                tax_amount = EXCLUDED.tax_amount,
                subtotal = EXCLUDED.subtotal,
                total = EXCLUDED.total,
                source_updated_at = EXCLUDED.source_updated_at,
                loaded_at = NOW()

            WHERE fact_sales.source_updated_at IS NULL
               OR fact_sales.source_updated_at
                  < EXCLUDED.source_updated_at
            """;

        return jdbcTemplate.update(sql, executionId);
    }
}
