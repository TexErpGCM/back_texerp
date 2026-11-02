-- Sprint 4: actualización de la estructura de auditoría.
-- Agregar columna faltante para almacenar el nuevo valor.

ALTER TABLE audit_event
    ADD COLUMN IF NOT EXISTS new_value TEXT;

