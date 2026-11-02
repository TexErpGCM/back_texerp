
package co.texerp.integrations.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class EtlSalesExtractionService {

    private final JdbcTemplate jdbcTemplate;

    public EtlSalesExtractionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int extract(
            Long executionId,
            OffsetDateTime watermarkFrom,
            OffsetDateTime watermarkTo
    ) {

        if (executionId == null || executionId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de ejecución no es válido"
            );
        }

        if (watermarkFrom == null || watermarkTo == null) {
            throw new IllegalArgumentException(
                    "Los watermarks son obligatorios"
            );
        }

        if (watermarkFrom.isAfter(watermarkTo)) {
            throw new IllegalArgumentException(
                    "El watermark inicial no puede superar al final"
            );
        }

        // 1. Registrar registros inválidos antes de staging.
        jdbcTemplate.update(
                """
                INSERT INTO texerp_dw.staging_rejections (
                    execution_id,
                    job_id,
                    source_table,
                    source_record_id,
                    rejection_reason,
                    raw_data
                )
                SELECT
                    e.id,
                    e.job_id,
                    'sale_line',
                    sl.id::TEXT,
                    CONCAT_WS(
                        '; ',
                        CASE
                            WHEN s.id IS NULL
                            THEN 'Identificador de venta obligatorio'
                        END,
                        CASE
                            WHEN s.confirmed_at IS NULL
                            THEN 'Fecha de confirmación obligatoria'
                        END
                    ),
                    jsonb_build_object(
                        'sale_id', s.id,
                        'sale_line_id', sl.id,
                        'status', s.status,
                        'confirmed_at', s.confirmed_at,
                        'variant_id', sl.variant_id,
                        'source_updated_at',
                            GREATEST(
                                s.updated_at,
                                sl.updated_at
                            )
                    )
                FROM public.sale s
                INNER JOIN public.sale_line sl
                    ON sl.sale_id = s.id
                INNER JOIN texerp_dw.etl_execution e
                    ON e.id = ?
                WHERE GREATEST(
                        s.updated_at,
                        sl.updated_at
                      ) > ?
                  AND GREATEST(
                        s.updated_at,
                        sl.updated_at
                      ) <= ?
                  AND s.status IN (
                      'CONFIRMED',
                      'DISPATCHED',
                      'COMPLETED'
                  )
                  AND (
                      s.id IS NULL
                      OR s.confirmed_at IS NULL
                  )
                ON CONFLICT DO NOTHING
                """,
                executionId,
                watermarkFrom,
                watermarkTo
        );

        // 2. Extraer únicamente ventas válidas.
        jdbcTemplate.update(
                """
                INSERT INTO texerp_dw.stg_sales (
                    execution_id,
                    sale_line_id,
                    sale_id,
                    sale_number,
                    customer_id,
                    warehouse_id,
                    variant_id,
                    sale_status,
                    confirmed_at,
                    source_updated_at,
                    quantity,
                    unit_price,
                    unit_cost,
                    discount_amount,
                    tax_amount,
                    subtotal,
                    total
                )
                SELECT
                    ?,
                    sl.id,
                    s.id,
                    s.number,
                    s.customer_id,
                    s.warehouse_id,
                    sl.variant_id,
                    s.status,
                    s.confirmed_at,
                    GREATEST(
                        s.updated_at,
                        sl.updated_at
                    ),
                    sl.quantity,
                    sl.unit_price,
                    sl.unit_cost,
                    sl.discount_amount,
                    sl.tax_amount,
                    sl.subtotal,
                    sl.total
                FROM public.sale s
                INNER JOIN public.sale_line sl
                    ON sl.sale_id = s.id
                WHERE GREATEST(
                        s.updated_at,
                        sl.updated_at
                      ) > ?
                  AND GREATEST(
                        s.updated_at,
                        sl.updated_at
                      ) <= ?
                  AND s.status IN (
                      'CONFIRMED',
                      'DISPATCHED',
                      'COMPLETED'
                  )
                  AND s.id IS NOT NULL
                  AND s.confirmed_at IS NOT NULL

                ON CONFLICT (sale_line_id)
                DO UPDATE SET
                    execution_id = EXCLUDED.execution_id,
                    sale_id = EXCLUDED.sale_id,
                    sale_number = EXCLUDED.sale_number,
                    customer_id = EXCLUDED.customer_id,
                    warehouse_id = EXCLUDED.warehouse_id,
                    variant_id = EXCLUDED.variant_id,
                    sale_status = EXCLUDED.sale_status,
                    confirmed_at = EXCLUDED.confirmed_at,
                    source_updated_at = EXCLUDED.source_updated_at,
                    quantity = EXCLUDED.quantity,
                    unit_price = EXCLUDED.unit_price,
                    unit_cost = EXCLUDED.unit_cost,
                    discount_amount = EXCLUDED.discount_amount,
                    tax_amount = EXCLUDED.tax_amount,
                    subtotal = EXCLUDED.subtotal,
                    total = EXCLUDED.total,
                    staged_at = NOW()
                WHERE texerp_dw.stg_sales.source_updated_at
                      <= EXCLUDED.source_updated_at
                """,
                executionId,
                watermarkFrom,
                watermarkTo
        );

        // 3. Contar líneas examinadas en la ventana.
        Integer recordsRead = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.sale s
                INNER JOIN public.sale_line sl
                    ON sl.sale_id = s.id
                WHERE GREATEST(
                        s.updated_at,
                        sl.updated_at
                      ) > ?
                  AND GREATEST(
                        s.updated_at,
                        sl.updated_at
                      ) <= ?
                  AND s.status IN (
                      'CONFIRMED',
                      'DISPATCHED',
                      'COMPLETED'
                  )
                """,
                Integer.class,
                watermarkFrom,
                watermarkTo
        );

        return recordsRead == null ? 0 : recordsRead;
    }
}
