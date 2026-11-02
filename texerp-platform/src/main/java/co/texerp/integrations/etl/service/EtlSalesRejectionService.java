
package co.texerp.integrations.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EtlSalesRejectionService {

    private final JdbcTemplate jdbcTemplate;

    public EtlSalesRejectionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int rejectInvalidSales(Long executionId) {

        if (executionId == null || executionId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de ejecución no es válido"
            );
        }

        String sql = """
            INSERT INTO texerp_dw.staging_rejections (
                execution_id,
                job_id,
                source_table,
                source_record_id,
                rejection_reason,
                raw_data
            )
            SELECT
                e.id,
                e.job_id,
                'stg_sales',
                s.sale_line_id::TEXT,
                CONCAT_WS(
                    '; ',
                    CASE
                        WHEN s.sale_id IS NULL
                        THEN 'Identificador de venta obligatorio'
                    END,
                    CASE
                        WHEN s.confirmed_at IS NULL
                             AND s.sale_status IN (
                                 'CONFIRMED',
                                 'DISPATCHED',
                                 'COMPLETED'
                             )
                        THEN 'Fecha de confirmación obligatoria'
                    END
                ),
                to_jsonb(s)
            FROM texerp_dw.stg_sales s
            INNER JOIN texerp_dw.etl_execution e
                ON e.id = s.execution_id
            WHERE s.execution_id = ?
              AND (
                  s.sale_id IS NULL
                  OR (
                      s.confirmed_at IS NULL
                      AND s.sale_status IN (
                          'CONFIRMED',
                          'DISPATCHED',
                          'COMPLETED'
                      )
                  )
              )
            ON CONFLICT DO NOTHING
            """;

        return jdbcTemplate.update(sql, executionId);
    }
}
