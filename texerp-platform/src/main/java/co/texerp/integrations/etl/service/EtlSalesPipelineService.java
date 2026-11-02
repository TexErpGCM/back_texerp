
package co.texerp.integrations.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class EtlSalesPipelineService {

    private final JdbcTemplate jdbcTemplate;
    private final EtlSalesExtractionService extractionService;
    private final EtlSalesDimensionService dimensionService;
    private final EtlSalesAlertService alertService;
    private final EtlSalesFactService factService;
    private final EtlSalesValidationService validationService;

    public EtlSalesPipelineService(
            JdbcTemplate jdbcTemplate,
            EtlSalesExtractionService extractionService,
            EtlSalesDimensionService dimensionService,
            EtlSalesAlertService alertService,
            EtlSalesFactService factService,
            EtlSalesValidationService validationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.extractionService = extractionService;
        this.dimensionService = dimensionService;
        this.alertService = alertService;
        this.factService = factService;
        this.validationService = validationService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void execute(Long executionId) {

        if (executionId == null || executionId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de ejecución no es válido"
            );
        }

        // 1. Bloquear la ejecución actual.
        Long jobId = jdbcTemplate.query(
                """
                SELECT job_id
                FROM texerp_dw.etl_execution
                WHERE id = ?
                  AND status = 'RUNNING'
                FOR UPDATE
                """,
                rs -> rs.next()
                        ? rs.getLong("job_id")
                        : null,
                executionId
        );

        if (jobId == null) {
            throw new IllegalStateException(
                    "La ejecución " + executionId
                            + " no existe o no está en estado RUNNING"
            );
        }

        // 2. Límite superior de extracción.
        OffsetDateTime watermarkTo =
                OffsetDateTime.now(ZoneOffset.UTC);

        // 3. Último watermark exitoso.
        OffsetDateTime watermarkFrom = jdbcTemplate.query(
                """
                SELECT last_successful_watermark
                FROM texerp_dw.etl_watermark
                WHERE job_id = ?
                """,
                rs -> rs.next()
                        ? rs.getObject(
                        "last_successful_watermark",
                        OffsetDateTime.class
                )
                        : null,
                jobId
        );

        if (watermarkFrom == null) {
            watermarkFrom = OffsetDateTime.parse(
                    "1970-01-01T00:00:00Z"
            );
        }

        if (watermarkFrom.isAfter(watermarkTo)) {
            throw new IllegalStateException(
                    "El watermark inicial es posterior al final"
            );
        }

        // 4. Ventana de seguridad.
        OffsetDateTime extractionFrom =
                watermarkFrom.minusMinutes(5);

        // 5. Extracción incremental y rechazos de origen.
        int recordsRead = extractionService.extract(
                executionId,
                extractionFrom,
                watermarkTo
        );

        // 6. Contar rechazos.
        int recordsRejected =
                validationService.countRejections(executionId);

        // 7. Actualizar dimensiones SCD Tipo 2.
        dimensionService.loadDimensions();

        // 8. Registrar alertas por productos desconocidos.
        alertService.registerUnknownProducts(executionId);

        // 9. Validar dimensiones obligatorias.
        factService.validateDimensions(executionId);

        // 10. Cargar hechos.
        int recordsWritten =
                factService.loadFacts(executionId);

        // 11. Finalizar ejecución.
        int updatedExecutions = jdbcTemplate.update(
                """
                UPDATE texerp_dw.etl_execution
                SET status = 'SUCCESS',
                    finished_at = NOW(),
                    watermark_from = ?,
                    watermark_to = ?,
                    records_read = ?,
                    records_written = ?,
                    records_rejected = ?,
                    failed_step = NULL,
                    error_message = NULL
                WHERE id = ?
                  AND status = 'RUNNING'
                """,
                watermarkFrom,
                watermarkTo,
                recordsRead,
                recordsWritten,
                recordsRejected,
                executionId
        );

        if (updatedExecutions != 1) {
            throw new IllegalStateException(
                    "No fue posible finalizar la ejecución ETL "
                            + executionId
            );
        }

        // 12. Avanzar watermark en la misma transacción.
        jdbcTemplate.update(
                """
                INSERT INTO texerp_dw.etl_watermark (
                    job_id,
                    last_successful_watermark,
                    updated_at
                )
                VALUES (?, ?, NOW())
                ON CONFLICT (job_id)
                DO UPDATE SET
                    last_successful_watermark =
                        EXCLUDED.last_successful_watermark,
                    updated_at = NOW()
                """,
                jobId,
                watermarkTo
        );
    }
}
