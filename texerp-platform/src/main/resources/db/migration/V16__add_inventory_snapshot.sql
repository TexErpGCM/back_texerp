
CREATE TABLE IF NOT EXISTS texerp_dw.inventory_snapshot_closure (
                                                                    snapshot_date DATE PRIMARY KEY,
                                                                    status VARCHAR(20) NOT NULL,
    closed_at TIMESTAMPTZ,
    closed_by VARCHAR(150),

    CONSTRAINT ck_inventory_snapshot_closure_status
    CHECK (status IN ('PRELIMINARY', 'CLOSED')),

    CONSTRAINT ck_inventory_snapshot_closure_closed
    CHECK (
              status = 'PRELIMINARY'
              OR (closed_at IS NOT NULL AND closed_by IS NOT NULL)
    )
    );

INSERT INTO texerp_dw.inventory_snapshot_closure (
    snapshot_date,
    status
)
SELECT DISTINCT
    snapshot_date,
    'PRELIMINARY'
FROM texerp_dw.fact_inventory
    ON CONFLICT (snapshot_date) DO NOTHING;
