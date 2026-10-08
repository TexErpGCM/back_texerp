package co.texerp.integrations.domain;

import java.util.Set;

public enum Role {

    ADMINISTRADOR(
            Set.of(
                    Permission.DASHBOARD_READ,

                    Permission.USER_READ,
                    Permission.USER_CREATE,
                    Permission.USER_UPDATE,
                    Permission.USER_DELETE,

                    Permission.AUDIT_READ,

                    Permission.INVENTORY_READ,
                    Permission.INVENTORY_MOVEMENT_READ,
                    Permission.INVENTORY_ADJUST,
                    Permission.INVENTORY_NEGATIVE_ADJUST,

                    Permission.QUOTATION_READ,
                    Permission.QUOTATION_CREATE,
                    Permission.QUOTATION_UPDATE,
                    Permission.QUOTATION_CONVERT,
                    Permission.SALE_READ,
                    Permission.SALE_CREATE,
                    Permission.PAYMENT_CREATE
            )
    ),

    ANALISTA(
            Set.of(
                    Permission.DASHBOARD_READ,

                    Permission.INVENTORY_READ,
                    Permission.INVENTORY_MOVEMENT_READ,

                    Permission.QUOTATION_READ,
                    Permission.SALE_READ
            )
    ),

    VENDEDOR(
            Set.of(
                    Permission.DASHBOARD_READ,
                    Permission.INVENTORY_READ,
                    Permission.QUOTATION_READ,
                    Permission.QUOTATION_CREATE,
                    Permission.QUOTATION_UPDATE,
                    Permission.QUOTATION_CONVERT,
                    Permission.SALE_READ,
                    Permission.SALE_CREATE,
                    Permission.PAYMENT_CREATE
            )
    );

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
