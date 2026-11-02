
package co.texerp.integrations.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class EtlExecutionStatusService {

    private final JdbcTemplate jdbcTemplate;

    public EtlExecutionStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(Long executionId) {

        int updated = jdbcTemplate.update(
                """
                UPDATE texerp_dw.etl_execution
                SET status = 'RUNNING',
                    started_at = ?,
                    finished_at = NULL,
                    failed_step = NULL,
                    error_message = NULL
                WHERE id = ?
                  AND status = 'PENDING'
                """,
                OffsetDateTime.now(ZoneOffset.UTC),
                executionId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "No fue posible iniciar la ejecución ETL "
                            + executionId
                            + ". Debe existir y estar en PENDING."
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            Long executionId,
            String failedStep,
            String errorMessage
    ) {

        int updated = jdbcTemplate.update(
                """
                UPDATE texerp_dw.etl_execution
                SET status = 'FAILED',
                    finished_at = ?,
                    failed_step = ?,
                    error_message = ?
                WHERE id = ?
                  AND status IN ('PENDING', 'RUNNING')
                """,
                OffsetDateTime.now(ZoneOffset.UTC),
                failedStep,
                errorMessage,
                executionId
        );

        if (updated == 0) {
            // No sobrescribir SUCCESS ni FAILED.
            // Tampoco modificar ejecuciones inexistentes.
            return;
        }

        if (updated != 1) {
            throw new IllegalStateException(
                    "Actualización inesperada de estado ETL: "
                            + executionId
            );
        }
    }
}
