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

                    Permission.AUDIT_READ
            )
    ),

    ANALISTA(
            Set.of(
                    Permission.DASHBOARD_READ
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