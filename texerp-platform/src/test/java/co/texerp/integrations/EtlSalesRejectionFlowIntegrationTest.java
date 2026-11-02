
package co.texerp.integrations.etl.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EtlSalesRejectionFlowIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EtlSalesValidationService validationService;

    @Test
    void shouldRegisterRejectionWithoutLoadingFact() {

        Long executionId = jdbcTemplate.queryForObject("""
            SELECT id
            FROM texerp_dw.etl_execution
            ORDER BY id DESC
            LIMIT 1
            """, Long.class);

        assertNotNull(executionId);

        Long invalidSaleLineId = -999999L;

        int initialRejections =
                validationService.countRejections(executionId);

        int inserted = validationService.registerRejection(
                executionId,
                invalidSaleLineId,
                null,
                "CONFIRMED",
                "Identificador de venta obligatorio"
        );

        assertEquals(1, inserted);

        int finalRejections =
                validationService.countRejections(executionId);

        assertEquals(
                initialRejections + 1,
                finalRejections
        );

        Integer factCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM texerp_dw.fact_sales
            WHERE sale_line_id = ?
            """, Integer.class, invalidSaleLineId);

        assertEquals(0, factCount);

        Integer rejectionCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM texerp_dw.staging_rejections
            WHERE execution_id = ?
              AND source_table = 'sale_line'
              AND source_record_id = ?
            """,
                Integer.class,
                executionId,
                invalidSaleLineId.toString()
        );

        assertEquals(1, rejectionCount);
    }
}
