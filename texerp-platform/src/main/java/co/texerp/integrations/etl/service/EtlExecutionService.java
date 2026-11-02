
package co.texerp.integrations.etl.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.function.Supplier;

@Service
public class EtlExecutionService {

    private final EtlExecutionRegistrationService registrationService;
    private final EtlExecutionDispatcher dispatcher;

    public EtlExecutionService(
            EtlExecutionRegistrationService registrationService,
            EtlExecutionDispatcher dispatcher
    ) {
        this.registrationService = registrationService;
        this.dispatcher = dispatcher;
    }

    public Long requestExecution(String jobName) {
        return registerAndDispatch(
                () -> registrationService.register(jobName)
        );
    }

    public Long requestRetry(Long previousExecutionId) {

        if (previousExecutionId == null || previousExecutionId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El identificador de ejecución no es válido"
            );
        }

        return registerAndDispatch(
                () -> registrationService.registerRetry(
                        previousExecutionId
                )
        );
    }

    public Long requestScheduledExecution(String jobName) {
        return registerAndDispatch(
                () -> registrationService.registerScheduled(jobName)
        );
    }

    private Long registerAndDispatch(Supplier<Long> registration) {

        final Long executionId;

        try {
            executionId = registration.get();

        } catch (DataIntegrityViolationException ex) {

            if (isActiveExecutionConflict(ex)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El job ya tiene una ejecución activa",
                        ex
                );
            }

            throw ex;
        }

        dispatcher.dispatch(executionId);
        return executionId;
    }

    private boolean isActiveExecutionConflict(Throwable error) {

        Throwable current = error;

        while (current != null) {

            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(
                    "ux_etl_execution_active_job"
            )) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
