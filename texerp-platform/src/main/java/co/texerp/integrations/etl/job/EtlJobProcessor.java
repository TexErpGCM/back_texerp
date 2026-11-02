
package co.texerp.integrations.etl.job;

import co.texerp.integrations.etl.service.EtlExecutionStatusService;
import co.texerp.integrations.etl.service.EtlSalesPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EtlJobProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(EtlJobProcessor.class);

    private final EtlExecutionStatusService statusService;
    private final EtlSalesPipelineService salesPipelineService;

    public EtlJobProcessor(
            EtlExecutionStatusService statusService,
            EtlSalesPipelineService salesPipelineService
    ) {
        this.statusService = statusService;
        this.salesPipelineService = salesPipelineService;
    }

    public void process(Long executionId) {

        log.info(
                "Iniciando procesamiento ETL. executionId={}",
                executionId
        );

        try {
            // Cambiar PENDING -> RUNNING.
            statusService.markRunning(executionId);


            if (Boolean.getBoolean("texerp.etl.test-concurrency")) {
                try {
                    Thread.sleep(15_000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Prueba de concurrencia interrumpida",
                            ex
                    );
                }
            }


            // Ejecutar el pipeline real.
            // Este método tiene su propia transacción.
            salesPipelineService.execute(executionId);

            log.info(
                    "Ejecución ETL finalizada correctamente. executionId={}",
                    executionId
            );

        } catch (Exception ex) {

            log.error(
                    "Error procesando ejecución ETL {}",
                    executionId,
                    ex
            );

            try {
                // Se ejecuta después del rollback del pipeline.
                // markFailed utiliza REQUIRES_NEW.
                statusService.markFailed(
                        executionId,
                        "ETL_PROCESSING",
                        getErrorMessage(ex)
                );

            } catch (Exception statusError) {
                log.error(
                        "No se pudo registrar FAILED para ejecución {}",
                        executionId,
                        statusError
                );
            }
        }
    }

    private String getErrorMessage(Exception ex) {

        String message = ex.getMessage();

        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }

        // Evitar mensajes excesivamente grandes.
        return message.length() > 2000
                ? message.substring(0, 2000)
                : message;
    }
}
