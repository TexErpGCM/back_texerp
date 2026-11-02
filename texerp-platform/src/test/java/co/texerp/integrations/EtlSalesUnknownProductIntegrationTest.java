package co.texerp.integrations;

import co.texerp.integrations.etl.service.EtlSalesAlertService;
import co.texerp.integrations.etl.service.EtlSalesFactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EtlSalesUnknownProductIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EtlSalesAlertService alertService;

    @Autowired
    private EtlSalesFactService factService;

    @Test
    void shouldRegisterAlertAndUseUnknownProduct() {

        // 1. Seleccionar una línea existente en staging.
        Long saleLineId = jdbcTemplate.queryForObject(
                """
                SELECT MIN(sale_line_id)
                FROM texerp_dw.stg_sales
                """,
                Long.class
        );

        assertNotNull(
                saleLineId,
                "Se necesita al menos una línea en staging"
        );

        // 2. Obtener su ejecución asociada.
        Long executionId = jdbcTemplate.queryForObject(
                """
                SELECT execution_id
                FROM texerp_dw.stg_sales
                WHERE sale_line_id = ?
                """,
                Long.class,
                saleLineId
        );

        assertNotNull(executionId);

        // 3. Simular una variante desconocida y una
        // actualización posterior a la del hecho.
        int updated = jdbcTemplate.update(
                """
                UPDATE texerp_dw.stg_sales
                SET variant_id = -999999,
                    source_updated_at = GREATEST(
                        source_updated_at,
                        COALESCE(
                            (
                                SELECT f.source_updated_at
                                FROM texerp_dw.fact_sales f
                                WHERE f.sale_line_id = ?
                            ),
                            source_updated_at
                        ),
                        NOW()
                    ) + INTERVAL '1 second'
                WHERE sale_line_id = ?
                """,
                saleLineId,
                saleLineId
        );

        assertEquals(1, updated);

        // 4. Registrar alerta con el servicio real.
        int alertsInserted =
                alertService.registerUnknownProducts(executionId);

        assertEquals(
                1,
                alertsInserted,
                "Debe registrarse una alerta UNKNOWN_PRODUCT"
        );

        // 5. Comprobar que la alerta existe.
        Integer alertCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM texerp_dw.etl_alert
                WHERE execution_id = ?
                  AND alert_type = 'UNKNOWN_PRODUCT'
                  AND source_record_id = ?
                """,
                Integer.class,
                executionId,
                saleLineId.toString()
        );

        assertEquals(1, alertCount);

        // 6. Validar dimensiones y cargar hechos.
        factService.validateDimensions(executionId);

        int written = factService.loadFacts(executionId);

        assertTrue(
                written >= 1,
                "Debe insertarse o actualizarse al menos un hecho"
        );

        // 7. Obtener la clave de la dimensión Desconocido.
        Long unknownProductId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM texerp_dw.dim_product
                WHERE variant_id = -1
                  AND is_current = TRUE
                """,
                Long.class
        );

        assertNotNull(unknownProductId);

        // 8. Verificar la clave asignada al hecho.
        Long factProductKey = jdbcTemplate.queryForObject(
                """
                SELECT product_key
                FROM texerp_dw.fact_sales
                WHERE sale_line_id = ?
                """,
                Long.class,
                saleLineId
        );

        assertEquals(
                unknownProductId,
                factProductKey,
                "El hecho debe utilizar la dimensión Desconocido"
        );

        // 9. Verificar que la línea no fue rechazada.
        Integer rejectedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM texerp_dw.staging_rejections
                WHERE execution_id = ?
                  AND source_table IN ('sale_line', 'stg_sales')
                  AND source_record_id = ?
                """,
                Integer.class,
                executionId,
                saleLineId.toString()
        );

        assertEquals(
                0,
                rejectedCount,
                "Un producto desconocido permitido no debe rechazarse"
        );

        // Spring revierte automáticamente esta transacción
        // al terminar la prueba.
    }
}
