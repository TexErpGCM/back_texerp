-- Sprint 4: agregar valor anterior para auditoría.

ALTER TABLE audit_event
    ADD COLUMN IF NOT EXISTS old_value TEXT;