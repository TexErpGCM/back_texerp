-- Agregar columnas de auditoría a las recepciones
ALTER TABLE purchase_receipt
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Agregar columnas de auditoría a los detalles de recepción
ALTER TABLE purchase_receipt_line
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP;