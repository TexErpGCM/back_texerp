package co.texerp.integrations.domain;

public enum Permission {

    DASHBOARD_READ("Dashboard", "Consultar dashboard"),

    USER_READ("Usuarios", "Consultar usuarios"),
    USER_CREATE("Usuarios", "Crear usuarios"),
    USER_UPDATE("Usuarios", "Actualizar usuarios"),
    USER_DELETE("Usuarios", "Eliminar usuarios"),

    ROLE_READ("Roles y permisos", "Consultar roles"),
    ROLE_CREATE("Roles y permisos", "Crear roles"),
    ROLE_UPDATE("Roles y permisos", "Actualizar roles y permisos"),
    ROLE_DELETE("Roles y permisos", "Eliminar roles"),
    PERMISSION_READ("Roles y permisos", "Consultar catálogo de permisos"),

    AUDIT_READ("Auditoría", "Consultar auditoría"),

    CUSTOMER_READ("Clientes", "Consultar clientes"),
    CUSTOMER_CREATE("Clientes", "Crear clientes"),
    CUSTOMER_UPDATE("Clientes", "Actualizar clientes"),

    WAREHOUSE_READ("Bodegas", "Consultar bodegas"),
    WAREHOUSE_CREATE("Bodegas", "Crear bodegas"),
    WAREHOUSE_UPDATE("Bodegas", "Actualizar bodegas"),

    PRODUCT_READ("Productos", "Consultar productos"),
    PRODUCT_CREATE("Productos", "Crear productos"),
    PRODUCT_UPDATE("Productos", "Actualizar productos"),

    PRODUCT_VARIANT_READ("Variantes", "Consultar variantes"),
    PRODUCT_VARIANT_CREATE("Variantes", "Crear variantes"),
    PRODUCT_VARIANT_UPDATE("Variantes", "Actualizar variantes"),

    SUPPLIER_READ("Proveedores", "Consultar proveedores"),
    SUPPLIER_CREATE("Proveedores", "Crear proveedores"),
    SUPPLIER_UPDATE("Proveedores", "Actualizar proveedores"),

    INVENTORY_READ("Inventario", "Consultar inventario"),
    INVENTORY_MOVEMENT_READ("Inventario", "Consultar movimientos"),
    INVENTORY_ADJUST("Inventario", "Registrar ajustes"),
    INVENTORY_NEGATIVE_ADJUST("Inventario", "Permitir ajustes negativos"),

    QUOTATION_READ("Cotizaciones", "Consultar cotizaciones"),
    QUOTATION_CREATE("Cotizaciones", "Crear cotizaciones"),
    QUOTATION_UPDATE("Cotizaciones", "Enviar o cancelar cotizaciones"),
    QUOTATION_CONVERT("Cotizaciones", "Convertir cotizaciones a venta"),

    SALE_READ("Ventas", "Consultar ventas"),
    SALE_CREATE("Ventas", "Crear ventas"),

    PAYMENT_CREATE("Pagos", "Registrar pagos");

    private final String module;
    private final String description;

    Permission(String module, String description) {
        this.module = module;
        this.description = description;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }
}
