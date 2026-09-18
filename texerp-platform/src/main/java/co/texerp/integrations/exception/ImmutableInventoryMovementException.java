package co.texerp.integrations.exception;

public class ImmutableInventoryMovementException extends RuntimeException {

    public ImmutableInventoryMovementException() {
        super("Los movimientos de inventario confirmados son inmutables. La corrección debe realizarse mediante un movimiento compensatorio");
    }
}
