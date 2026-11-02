
-- Identificar la ejecución propietaria de cada fila de staging.
-- El staging actual usa sale_line_id como clave primaria,
-- por lo que conserva una sola versión por línea de venta.

ALTER TABLE texerp_dw.stg_sales
    ADD COLUMN execution_id BIGINT;

ALTER TABLE texerp_dw.stg_sales
    ADD CONSTRAINT fk_stg_sales_execution
        FOREIGN KEY (execution_id)
            REFERENCES texerp_dw.etl_execution(id);

CREATE INDEX ix_stg_sales_execution_id
    ON texerp_dw.stg_sales(execution_id);

-- Las filas anteriores a esta migración quedan sin lote asignado.
-- No se incluirán en las nuevas cargas filtradas por execution_id.
