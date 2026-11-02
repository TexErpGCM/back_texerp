
package co.texerp.integrations;

import co.texerp.integrations.analytics.service.PurchaseDwSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PurchaseDwTestConfig {

    private final PurchaseDwSyncService purchaseDwSyncService;

    @Bean
    public ApplicationRunner testPurchaseDwSync() {
        return args -> {
            log.info("HU-29: iniciando sincronización manual");

            try {
                int changed = purchaseDwSyncService.synchronize();

                log.info(
                        "HU-29: sincronización exitosa. Líneas modificadas: {}",
                        changed
                );
            } catch (Exception e) {
                log.error("HU-29: error al sincronizar compras", e);
                throw e;
            }
        };
    }
}
