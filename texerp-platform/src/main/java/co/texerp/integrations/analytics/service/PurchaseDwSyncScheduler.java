package co.texerp.integrations.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "texerp.analytics.purchases.sync", name = "enabled", havingValue = "true")
public class PurchaseDwSyncScheduler {
    private final PurchaseDwSyncService service;

    @Scheduled(cron = "${texerp.analytics.purchases.sync.cron:0 */5 * * * *}", zone = "America/Bogota")
    public void run() {
        try {
            int changed = service.synchronize();
            log.info("Purchase DW sync completed, changed lines={}", changed);
        } catch (Exception e) {
            log.error("Purchase DW sync failed", e);
        }
    }
}
