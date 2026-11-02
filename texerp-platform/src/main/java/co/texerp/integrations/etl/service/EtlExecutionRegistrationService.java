
package co.texerp.integrations.etl.service;

import co.texerp.integrations.etl.domain.EtlExecution;
import co.texerp.integrations.etl.domain.EtlJob;
import co.texerp.integrations.etl.repository.EtlExecutionRepository;
import co.texerp.integrations.etl.repository.EtlJobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EtlExecutionRegistrationService {

    private final EtlJobRepository jobRepository;
    private final EtlExecutionRepository executionRepository;

    public EtlExecutionRegistrationService(
            EtlJobRepository jobRepository,
            EtlExecutionRepository executionRepository
    ) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
    }

    @Transactional
    public Long register(String jobName) {

        EtlJob job = jobRepository.findByName(jobName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Job ETL no encontrado"
                ));

        validateEnabled(job);

        EtlExecution execution = new EtlExecution();
        execution.setJob(job);
        execution.setTriggerType("MANUAL");
        execution.setStatus("PENDING");

        return executionRepository.saveAndFlush(execution).getId();
    }

    @Transactional
    public Long registerRetry(Long previousExecutionId) {

        EtlExecution previous = executionRepository
                .findById(previousExecutionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ejecución ETL no encontrada"
                ));

        if (!"FAILED".equals(previous.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden reintentar ejecuciones FAILED"
            );
        }

        EtlJob job = previous.getJob();
        validateEnabled(job);

        EtlExecution retry = new EtlExecution();
        retry.setJob(job);
        retry.setTriggerType("RETRY");
        retry.setStatus("PENDING");
        retry.setPreviousExecution(previous);
        return executionRepository.saveAndFlush(retry).getId();
    }

    private void validateEnabled(EtlJob job) {
        if (!Boolean.TRUE.equals(job.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job ETL deshabilitado"
            );
        }
    }

    @Transactional
    public Long registerScheduled(String jobName) {

        EtlJob job = jobRepository.findByName(jobName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Job ETL no encontrado"
                ));

        validateEnabled(job);

        EtlExecution execution = new EtlExecution();
        execution.setJob(job);
        execution.setTriggerType("SCHEDULED");
        execution.setStatus("PENDING");

        return executionRepository.saveAndFlush(execution).getId();
    }
}
