
package co.texerp.integrations.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EtlSalesAlertService {

    private final JdbcTemplate jdbcTemplate;

    public EtlSalesAlertService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int registerUnknownProducts(Long executionId) {

        String sql = """
            INSERT INTO texerp_dw.etl_alert (
                execution_id,
                alert_type,
                source_record_id,
                message
            )
            SELECT
                s.execution_id,
                'UNKNOWN_PRODUCT',
                s.sale_line_id::TEXT,
                'No se encontró la variante de producto '
                    || COALESCE(s.variant_id::TEXT, 'NULL')
                    || ' para la línea de venta '
                    || s.sale_line_id
            FROM texerp_dw.stg_sales s
            LEFT JOIN texerp_dw.dim_product dp
                ON dp.variant_id = s.variant_id
               AND dp.is_current = TRUE
            WHERE s.execution_id = ?
              AND dp.id IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM texerp_dw.staging_rejections r
                  WHERE r.execution_id = s.execution_id
                    AND r.source_table IN ('sale_line', 'stg_sales')
                    AND r.source_record_id = s.sale_line_id::TEXT
              )
            ON CONFLICT DO NOTHING
            """;

        return jdbcTemplate.update(sql, executionId);
    }
}
