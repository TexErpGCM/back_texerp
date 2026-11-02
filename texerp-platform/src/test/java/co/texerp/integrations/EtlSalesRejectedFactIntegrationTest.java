
package co.texerp.integrations.etl.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EtlSalesRejectedFactIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EtlSalesValidationService validationService;

    @Autowired
    private EtlSalesFactService factService;

    @Test
    void shouldExcludeRejectedSaleFromFactLoad() {

        // 1. Buscar una línea que ya exista en staging.
        Long saleLineId = jdbcTemplate.queryForObject(
                """
                SELECT MIN(sale_line_id)
                FROM texerp_dw.stg_sales
                """,
                Long.class
        );

        assertNotNull(saleLineId);

        // 2. Obtener su ejecución original.
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

        // 3. Comprobar que no existe un rechazo previo.
        Integer previousRejections = jdbcTemplate.queryForObject(
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

        assertEquals(0, previousRejections);

        // 4. Registrar un rechazo real en la BD.
        int inserted = validationService.registerRejection(
                executionId,
                saleLineId,
                null,
                "CONFIRMED",
                "Fecha de confirmación obligatoria"
        );

        assertEquals(1, inserted);

        // 5. Contabilizar el rechazo.
        int rejectedCount =
                validationService.countRejections(executionId);

        assertTrue(rejectedCount >= 1);

        // 6. Guardar la versión actual del hecho.
        String originalFact = jdbcTemplate.queryForObject(
                """
                SELECT row_to_json(f)::TEXT
                FROM texerp_dw.fact_sales f
                WHERE f.sale_line_id = ?
                """,
                String.class,
                saleLineId
        );

        assertNotNull(originalFact);

        // 7. Cambiar temporalmente la fecha de actualización
        // de staging para simular una versión más reciente.
        jdbcTemplate.update(
                """
                UPDATE texerp_dw.stg_sales
                SET source_updated_at =
                    source_updated_at + INTERVAL '1 day'
                WHERE sale_line_id = ?
                """,
                saleLineId
        );

        // 8. Validar dimensiones.
        factService.validateDimensions(executionId);

        // 9. Ejecutar carga real de hechos.
        factService.loadFacts(executionId);

        // 10. Verificar que el hecho rechazado NO cambió.
        String finalFact = jdbcTemplate.queryForObject(
                """
                SELECT row_to_json(f)::TEXT
                FROM texerp_dw.fact_sales f
                WHERE f.sale_line_id = ?
                """,
                String.class,
                saleLineId
        );

        assertEquals(
                originalFact,
                finalFact,
                "Una línea rechazada no debe actualizar fact_sales"
        );

        // 11. Confirmar el rechazo.
        Integer rejectionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM texerp_dw.staging_rejections
                WHERE execution_id = ?
                  AND source_table = 'sale_line'
                  AND source_record_id = ?
                """,
                Integer.class,
                executionId,
                saleLineId.toString()
        );

        assertEquals(1, rejectionCount);
    }
}
