package co.texerp.integrations.domain;

public enum Permission {

    // Dashboard
    DASHBOARD_READ,

    // Usuarios
    USER_READ,
    USER_CREATE,
    USER_UPDATE,
    USER_DELETE,

    // Auditoría
    AUDIT_READ,

    // Inventario
    INVENTORY_READ,
    INVENTORY_MOVEMENT_READ,
    INVENTORY_ADJUST,
    INVENTORY_NEGATIVE_ADJUST,

    // Cotizaciones
    QUOTATION_READ,
    QUOTATION_CREATE,
    QUOTATION_UPDATE,
    QUOTATION_CONVERT,

    // Ventas
    SALE_READ,
    SALE_CREATE,

    // Pagos
    PAYMENT_CREATE
}
