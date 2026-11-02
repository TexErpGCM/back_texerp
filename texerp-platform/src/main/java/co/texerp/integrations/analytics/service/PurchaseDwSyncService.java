package co.texerp.integrations.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PurchaseDwSyncService {
    private final NamedParameterJdbcTemplate jdbc;

    // UPSERT completo e idempotente. También refleja correcciones y recepciones tardías.
    @Transactional
    public int synchronize() {
        jdbc.update("""
                INSERT INTO texerp_dw.dim_supplier
                  (supplier_id, tax_id, name, active, source_updated_at, loaded_at)
                SELECT id, tax_id, name, active, updated_at, now() FROM public.supplier
                ON CONFLICT (supplier_id) DO UPDATE SET
                  tax_id = EXCLUDED.tax_id,
                  name = EXCLUDED.name,
                  active = EXCLUDED.active,
                  source_updated_at = EXCLUDED.source_updated_at,
                  loaded_at = now()
                WHERE (dim_supplier.tax_id, dim_supplier.name, dim_supplier.active,
                       dim_supplier.source_updated_at) IS DISTINCT FROM
                      (EXCLUDED.tax_id, EXCLUDED.name, EXCLUDED.active,
                       EXCLUDED.source_updated_at)
                """, Map.of());

        return jdbc.update("""
                WITH receipts AS (
                    SELECT prl.purchase_order_line_id,
                           SUM(prl.quantity) AS received_quantity,
                           MIN(pr.received_at) AS first_received_at,
                           MAX(pr.received_at) AS last_received_at,
                           COUNT(DISTINCT pr.id) AS receipt_count,
                           MAX(GREATEST(pr.updated_at, prl.updated_at)) AS receipt_updated_at
                    FROM public.purchase_receipt_line prl
                    JOIN public.purchase_receipt pr ON pr.id = prl.receipt_id
                    GROUP BY prl.purchase_order_line_id
                )
                INSERT INTO texerp_dw.fact_purchases (
                    purchase_order_line_id, purchase_order_id, order_number,
                    supplier_id, product_variant_id, ordered_at, order_status,
                    ordered_quantity, received_quantity, ordered_amount, unit_cost,
                    first_received_at, last_received_at, receipt_count,
                    source_updated_at, loaded_at)
                SELECT pol.id, po.id, po.number, po.supplier_id, pol.variant_id,
                       po.ordered_at, po.status, pol.quantity,
                       COALESCE(r.received_quantity, 0), pol.total, pol.unit_cost,
                       r.first_received_at, r.last_received_at,
                       COALESCE(r.receipt_count, 0),
                       GREATEST(po.updated_at, pol.updated_at,
                                COALESCE(r.receipt_updated_at, po.updated_at)), now()
                FROM public.purchase_order_line pol
                JOIN public.purchase_order po ON po.id = pol.purchase_order_id
                LEFT JOIN receipts r ON r.purchase_order_line_id = pol.id
                ON CONFLICT (purchase_order_line_id) DO UPDATE SET
                    purchase_order_id = EXCLUDED.purchase_order_id,
                    order_number = EXCLUDED.order_number,
                    supplier_id = EXCLUDED.supplier_id,
                    product_variant_id = EXCLUDED.product_variant_id,
                    ordered_at = EXCLUDED.ordered_at,
                    order_status = EXCLUDED.order_status,
                    ordered_quantity = EXCLUDED.ordered_quantity,
                    received_quantity = EXCLUDED.received_quantity,
                    ordered_amount = EXCLUDED.ordered_amount,
                    unit_cost = EXCLUDED.unit_cost,
                    first_received_at = EXCLUDED.first_received_at,
                    last_received_at = EXCLUDED.last_received_at,
                    receipt_count = EXCLUDED.receipt_count,
                    source_updated_at = EXCLUDED.source_updated_at,
                    loaded_at = now()
                WHERE (fact_purchases.purchase_order_id, fact_purchases.order_number,
                       fact_purchases.supplier_id, fact_purchases.product_variant_id,
                       fact_purchases.ordered_at, fact_purchases.order_status,
                       fact_purchases.ordered_quantity, fact_purchases.received_quantity,
                       fact_purchases.ordered_amount, fact_purchases.unit_cost,
                       fact_purchases.first_received_at, fact_purchases.last_received_at,
                       fact_purchases.receipt_count, fact_purchases.source_updated_at)
                  IS DISTINCT FROM
                      (EXCLUDED.purchase_order_id, EXCLUDED.order_number,
                       EXCLUDED.supplier_id, EXCLUDED.product_variant_id,
                       EXCLUDED.ordered_at, EXCLUDED.order_status,
                       EXCLUDED.ordered_quantity, EXCLUDED.received_quantity,
                       EXCLUDED.ordered_amount, EXCLUDED.unit_cost,
                       EXCLUDED.first_received_at, EXCLUDED.last_received_at,
                       EXCLUDED.receipt_count, EXCLUDED.source_updated_at)
                """, Map.of());
    }
}
