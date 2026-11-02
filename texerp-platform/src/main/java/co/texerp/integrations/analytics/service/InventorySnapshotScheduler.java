
package co.texerp.integrations.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "texerp.inventory.snapshot",
        name = "enabled",
        havingValue = "true"
)
public class InventorySnapshotScheduler {

    private final InventorySnapshotService service;

    @Scheduled(
            cron = "${texerp.inventory.snapshot.cron:50 59 23 * * *}",
            zone = "America/Bogota"
    )
    public void capture() {
        var result = service.captureToday();

        log.info(
                "Inventory snapshot date={}, inserted={}, total={}",
                result.snapshotDate(),
                result.insertedRows(),
                result.totalRows()
        );
    }
}
