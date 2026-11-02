
package co.texerp.integrations.etl.service;

import co.texerp.integrations.etl.job.EtlJobProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class EtlExecutionDispatcher {

    private final Executor executor;
    private final EtlJobProcessor processor;
    private final EtlExecutionStatusService statusService;

    public EtlExecutionDispatcher(
            @Qualifier("etlTaskExecutor") Executor executor,
            EtlJobProcessor processor,
            EtlExecutionStatusService statusService
    ) {
        this.executor = executor;
        this.processor = processor;
        this.statusService = statusService;
    }

    public void dispatch(Long executionId) {

        try {
            executor.execute(() -> processor.process(executionId));

        } catch (RuntimeException ex) {

            statusService.markFailed(
                    executionId,
                    "DISPATCH",
                    "No fue posible programar la ejecución ETL"
            );

            throw ex;
        }
    }
}
