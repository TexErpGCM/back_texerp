
INSERT INTO texerp_dw.dim_product (
    variant_id,
    product_id,
    product_code,
    product_name,
    sku,
    color,
    pattern,
    fabric_type,
    sale_price,
    active,
    valid_from,
    valid_to,
    is_current
)
SELECT
    -1,
    -1,
    'UNKNOWN',
    'Desconocido',
    'UNKNOWN',
    'Desconocido',
    NULL,
    'Desconocido',
    0,
    FALSE,
    TIMESTAMPTZ '1970-01-01 00:00:00+00',
        NULL,
    TRUE
    WHERE NOT EXISTS (
    SELECT 1
    FROM texerp_dw.dim_product
    WHERE variant_id = -1
      AND is_current = TRUE
);
