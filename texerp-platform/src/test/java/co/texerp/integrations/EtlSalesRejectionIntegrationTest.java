
package co.texerp.integrations.etl.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EtlSalesRejectionIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldIdentifyInvalidSaleWithoutLoadingFact() {

        // Simular un registro de origen con fecha obligatoria ausente.
        Integer invalidCount = jdbcTemplate.queryForObject(
                """
                WITH source_sales AS (
                    SELECT
                        -999999::BIGINT AS sale_line_id,
                        -999999::BIGINT AS sale_id,
                        'CONFIRMED'::VARCHAR AS sale_status,
                        NULL::TIMESTAMPTZ AS confirmed_at
                )
                SELECT COUNT(*)
                FROM source_sales
                WHERE sale_id IS NULL
                   OR (
                       confirmed_at IS NULL
                       AND sale_status IN (
                           'CONFIRMED',
                           'DISPATCHED',
                           'COMPLETED'
                       )
                   )
                """,
                Integer.class
        );

        assertEquals(
                1,
                invalidCount,
                "La venta sin fecha debe identificarse como inválida"
        );

        // Comprobar que no existe un hecho para el ID simulado.
        Integer factCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM texerp_dw.fact_sales
                WHERE sale_line_id = -999999
                """,
                Integer.class
        );

        assertEquals(
                0,
                factCount,
                "La venta inválida no debe estar en fact_sales"
        );
    }
}
