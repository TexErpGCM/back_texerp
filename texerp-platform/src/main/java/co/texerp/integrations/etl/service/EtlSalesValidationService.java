
package co.texerp.integrations.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EtlSalesValidationService {

    private final JdbcTemplate jdbcTemplate;

    public EtlSalesValidationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int registerRejection(
            Long executionId,
            Long saleLineId,
            Long saleId,
            String status,
            String reason
    ) {

        if (executionId == null || saleLineId == null) {
            throw new IllegalArgumentException(
                    "La ejecución y la línea son obligatorias"
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "El motivo de rechazo es obligatorio"
            );
        }

        return jdbcTemplate.update("""
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
                'sale_line',
                ?::TEXT,
                ?,
                jsonb_build_object(
                    'sale_id', ?::BIGINT,
                    'sale_line_id', ?::BIGINT,
                    'status', ?::TEXT
                )
            FROM texerp_dw.etl_execution e
            WHERE e.id = ?
            ON CONFLICT DO NOTHING
            """,
                saleLineId,
                reason,
                saleId,
                saleLineId,
                status,
                executionId
        );
    }

    public int countRejections(Long executionId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM texerp_dw.staging_rejections
            WHERE execution_id = ?
            """, Integer.class, executionId);

        return count == null ? 0 : count;
    }
}
