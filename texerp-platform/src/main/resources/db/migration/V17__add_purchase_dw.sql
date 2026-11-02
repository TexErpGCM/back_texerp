-- Renombrar V__ al siguiente número libre de Flyway antes de aplicar.
CREATE SCHEMA IF NOT EXISTS texerp_dw;
CREATE TABLE IF NOT EXISTS texerp_dw.dim_supplier
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    supplier_id
    BIGINT
    NOT
    NULL
    UNIQUE,
    tax_id
    VARCHAR
    NOT
    NULL,
    name
    VARCHAR
    NOT
    NULL,
    active
    BOOLEAN
    NOT
    NULL,
    source_updated_at
    TIMESTAMPTZ
    NOT
    NULL,
    loaded_at
    TIMESTAMPTZ
    NOT
    NULL
    DEFAULT
    now
(
)
    );
CREATE TABLE IF NOT EXISTS texerp_dw.fact_purchases
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    purchase_order_line_id
    BIGINT
    NOT
    NULL
    UNIQUE,
    purchase_order_id
    BIGINT
    NOT
    NULL,
    order_number
    VARCHAR,
    supplier_id
    BIGINT
    NOT
    NULL
    REFERENCES
    texerp_dw
    .
    dim_supplier
(
    supplier_id
),
    product_variant_id BIGINT NOT NULL,
    ordered_at TIMESTAMPTZ NOT NULL,
    order_status VARCHAR NOT NULL,
    ordered_quantity NUMERIC NOT NULL,
    received_quantity NUMERIC NOT NULL DEFAULT 0,
    ordered_amount NUMERIC NOT NULL,
    unit_cost NUMERIC NOT NULL,
    first_received_at TIMESTAMPTZ,
    last_received_at TIMESTAMPTZ,
    receipt_count BIGINT NOT NULL DEFAULT 0,
    source_updated_at TIMESTAMPTZ NOT NULL,
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT now
(
)
    );
CREATE INDEX IF NOT EXISTS idx_fp_ordered ON texerp_dw.fact_purchases(ordered_at);
CREATE INDEX IF NOT EXISTS idx_fp_supplier ON texerp_dw.fact_purchases(supplier_id);
CREATE INDEX IF NOT EXISTS idx_fp_variant ON texerp_dw.fact_purchases(product_variant_id);
CREATE INDEX IF NOT EXISTS idx_fp_status ON texerp_dw.fact_purchases(order_status);
CREATE INDEX IF NOT EXISTS idx_dp_variant_current ON texerp_dw.dim_product(variant_id, is_current);
CREATE INDEX IF NOT EXISTS idx_pr_order_warehouse ON public.purchase_receipt(purchase_order_id, warehouse_id);
CREATE INDEX IF NOT EXISTS idx_prl_line ON public.purchase_receipt_line(purchase_order_line_id);
