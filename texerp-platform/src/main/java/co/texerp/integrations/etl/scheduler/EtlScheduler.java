
package co.texerp.integrations.etl.scheduler;

import co.texerp.integrations.etl.service.EtlExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class EtlScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(EtlScheduler.class);

    private static final ZoneId ZONE =
            ZoneId.of("America/Bogota");

    private final JdbcTemplate jdbcTemplate;
    private final EtlExecutionService executionService;

    public EtlScheduler(
            JdbcTemplate jdbcTemplate,
            EtlExecutionService executionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.executionService = executionService;
    }

    @Scheduled(
            cron = "0 * * * * *",
            zone = "America/Bogota"
    )
    public void executeScheduledJobs() {

        LocalDateTime now = LocalDateTime.now(ZONE)
                .withSecond(0)
                .withNano(0);

        LOGGER.info(
                "Scheduler ETL ejecutándose: {}",
                now
        );

        List<ScheduledJob> jobs;

        try {
            jobs = jdbcTemplate.query(
                    """
                    SELECT name, cron_expression
                    FROM texerp_dw.etl_job
                    WHERE enabled = TRUE
                      AND cron_expression IS NOT NULL
                      AND TRIM(cron_expression) <> ''
                    """,
                    (rs, rowNum) -> new ScheduledJob(
                            rs.getString("name"),
                            rs.getString("cron_expression")
                    )
            );
        } catch (Exception ex) {
            LOGGER.error(
                    "Error consultando los jobs ETL programados",
                    ex
            );
            return;
        }

        LOGGER.info(
                "Jobs ETL habilitados con cron: {}",
                jobs.size()
        );

        for (ScheduledJob job : jobs) {

            try {
                CronExpression expression =
                        CronExpression.parse(
                                job.cronExpression()
                        );

                LocalDateTime previousMinute =
                        now.minusMinutes(1);

                LocalDateTime nextExecution =
                        expression.next(previousMinute);

                if (!now.equals(nextExecution)) {
                    LOGGER.debug(
                            "El job {} no corresponde al minuto actual. " +
                                    "Próxima ejecución calculada: {}",
                            job.name(),
                            nextExecution
                    );
                    continue;
                }

                Long executionId =
                        executionService.requestScheduledExecution(
                                job.name()
                        );

                LOGGER.info(
                        "ETL programado iniciado. job={}, executionId={}",
                        job.name(),
                        executionId
                );

            } catch (IllegalArgumentException ex) {

                LOGGER.error(
                        "Cron inválido para el job {}: {}",
                        job.name(),
                        job.cronExpression(),
                        ex
                );

            } catch (ResponseStatusException ex) {

                if (ex.getStatusCode().value()
                        == HttpStatus.CONFLICT.value()) {

                    LOGGER.info(
                            "Se omite el job {} porque ya tiene " +
                                    "una ejecución activa",
                            job.name()
                    );

                } else {

                    LOGGER.error(
                            "No fue posible programar el job {}",
                            job.name(),
                            ex
                    );
                }

            } catch (Exception ex) {

                LOGGER.error(
                        "Error inesperado en el scheduler del job {}",
                        job.name(),
                        ex
                );
            }
        }
    }

    private record ScheduledJob(
            String name,
            String cronExpression
    ) {
    }
}
