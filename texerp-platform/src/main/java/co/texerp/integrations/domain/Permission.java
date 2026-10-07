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
    INVENTORY_NEGATIVE_ADJUST
}
