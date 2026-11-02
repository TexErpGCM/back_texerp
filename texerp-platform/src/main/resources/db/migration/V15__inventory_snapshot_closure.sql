
CREATE TABLE texerp_dw.inventory_snapshot_closure (
                                                      snapshot_date DATE PRIMARY KEY,
                                                      status VARCHAR(20) NOT NULL DEFAULT 'PRELIMINARY',
                                                      closed_at TIMESTAMPTZ,
                                                      closed_by VARCHAR(150),

                                                      CONSTRAINT ck_inventory_snapshot_status
                                                          CHECK (status IN ('PRELIMINARY', 'CLOSED'))
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

CREATE INDEX idx_inventory_snapshot_closure_status
    ON texerp_dw.inventory_snapshot_closure(status);
