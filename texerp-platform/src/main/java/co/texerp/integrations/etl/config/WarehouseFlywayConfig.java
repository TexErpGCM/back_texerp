
package co.texerp.integrations.etl.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class WarehouseFlywayConfig {

    @Bean
    @ConditionalOnProperty(
            name = "texerp.etl.enabled",
            havingValue = "true"
    )
    public ApplicationRunner warehouseMigrationRunner(
            DataSource dataSource,
            @Value("${texerp.etl.schema:texerp_dw}") String schema
    ) {
        return args -> {
            Flyway warehouseFlyway = Flyway.configure()
                    .dataSource(dataSource)
                    .defaultSchema(schema)
                    .schemas(schema)
                    .locations("classpath:db/warehouse")
                    .table("flyway_schema_history")
                    .load();

            warehouseFlyway.migrate();
        };
    }
}
