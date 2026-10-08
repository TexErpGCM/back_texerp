package co.texerp.integrations.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "app_role",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_app_role_name",
                columnNames = "name"
        )
)
public class Role extends BaseEntity {

    @Column(
            nullable = false,
            length = 80
    )
    public String name;

    @Column(length = 255)
    public String description;

    @Column(
            name = "system_role",
            nullable = false
    )
    public boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(
            name = "permission",
            nullable = false,
            length = 100
    )
    public Set<Permission> permissions =
            new LinkedHashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(
            String description
    ) {
        this.description = description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public void setSystemRole(
            boolean systemRole
    ) {
        this.systemRole = systemRole;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(
            Set<Permission> permissions
    ) {
        this.permissions =
                permissions != null
                        ? new LinkedHashSet<>(permissions)
                        : new LinkedHashSet<>();
    }
}