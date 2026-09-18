ALTER TABLE inventory_movement
    ADD CONSTRAINT ck_inventory_movement_adjustment_reason
        CHECK (
            movement_type <> 'ADJUSTMENT'
                OR (reason IS NOT NULL AND LENGTH(TRIM(reason)) > 0)
            );
