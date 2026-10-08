package co.texerp.integrations.dto;

import co.texerp.integrations.domain.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public final class RoleDtos {
    private RoleDtos() {}

    public record PermissionResponse(
            String code,
            String module,
            String description
    ) {}

    public record RoleSummary(
            Long id,
            String name,
            String description,
            boolean systemRole
    ) {}

    public record RoleResponse(
            Long id,
            String name,
            String description,
            boolean systemRole,
            Set<Permission> permissions,
            long activeUsers
    ) {}

    public record CreateRoleRequest(
            @NotBlank(message = "El nombre del rol es obligatorio")
            @Size(max = 80, message = "El nombre del rol no puede superar 80 caracteres")
            String name,

            @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
            String description,

            @NotEmpty(message = "El rol debe contener al menos un permiso")
            Set<Permission> permissions
    ) {}

    public record UpdateRoleRequest(
            @NotBlank(message = "El nombre del rol es obligatorio")
            @Size(max = 80, message = "El nombre del rol no puede superar 80 caracteres")
            String name,

            @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
            String description,

            @NotEmpty(message = "El rol debe contener al menos un permiso")
            Set<Permission> permissions
    ) {}

    public record RoleCatalogResponse(
            List<RoleResponse> roles,
            List<PermissionResponse> permissions
    ) {}
}
