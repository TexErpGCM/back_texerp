package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.Permission;
import co.texerp.integrations.domain.Role;
import co.texerp.integrations.dto.RoleDtos;
import co.texerp.integrations.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RoleService {

    private final RoleRepository repository;
    private final AuditService audit;

    public RoleService(RoleRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleDtos.RoleResponse> findAll() {
        return repository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleDtos.RoleResponse findById(Long id) {
        return toResponse(find(id));
    }

    public List<RoleDtos.PermissionResponse> permissionCatalog() {
        return Arrays.stream(Permission.values())
                .map(permission -> new RoleDtos.PermissionResponse(
                        permission.name(),
                        permission.getModule(),
                        permission.getDescription()
                ))
                .toList();
    }

    @Transactional
    public RoleDtos.RoleResponse create(
            RoleDtos.CreateRoleRequest request,
            HttpServletRequest http
    ) {
        String name = normalizeName(request.name());
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre del rol contiene caracteres inválidos");
        }

        if (repository.existsByNameIgnoreCase(name)) {
            throw new BusinessConflictException("Ya existe un rol con el nombre " + name);
        }

        Role role = new Role();
        role.name = name;
        role.description = normalizeDescription(request.description());
        role.systemRole = false;
        role.permissions = new LinkedHashSet<>(request.permissions());

        Role saved = repository.saveAndFlush(role);

        audit.log(
                "CREATE_ROLE",
                "Role",
                saved.id,
                "Rol creado: " + saved.name + ". Permisos: " + saved.permissions,
                http
        );

        return toResponse(saved);
    }

    @Transactional
    public RoleDtos.RoleResponse update(
            Long id,
            RoleDtos.UpdateRoleRequest request,
            HttpServletRequest http
    ) {
        Role role = find(id);
        String requestedName = normalizeName(request.name());

        if (repository.existsByNameIgnoreCaseAndIdNot(requestedName, id)) {
            throw new BusinessConflictException("Ya existe un rol con el nombre " + requestedName);
        }

        String previousName = role.name;
        String previousDescription = role.description;
        Set<Permission> previousPermissions = new LinkedHashSet<>(role.permissions);

        if (!role.systemRole) {
            role.name = requestedName;
        }
        role.description = normalizeDescription(request.description());
        role.permissions.clear();
        role.permissions.addAll(request.permissions());

        Role saved = repository.saveAndFlush(role);

        audit.log(
                "UPDATE_ROLE",
                "Role",
                saved.id,
                "Rol actualizado. name: " + previousName + " -> " + saved.name
                        + ", description: " + previousDescription + " -> " + saved.description
                        + ", permissions: " + previousPermissions + " -> " + saved.permissions,
                http
        );

        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest http) {
        Role role = find(id);
        long activeUsers = repository.countActiveUsersByRoleId(id);

        if (activeUsers > 0) {
            throw new BusinessConflictException(
                    "El rol está asignado a " + activeUsers
                            + " usuario(s) activo(s). Reasigna esos usuarios antes de eliminarlo"
            );
        }

        if (role.systemRole) {
            throw new BusinessConflictException(
                    "El rol " + role.name + " es un rol base del sistema y no puede eliminarse"
            );
        }

        repository.delete(role);

        audit.log(
                "DELETE_ROLE",
                "Role",
                id,
                "Rol eliminado: " + role.name,
                http
        );
    }

    @Transactional(readOnly = true)
    public Set<Role> requireRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new IllegalArgumentException("Debe asignarse al menos un rol");
        }

        Set<Role> roles = repository.findAllByIds(roleIds);

        if (roles.size() != roleIds.size()) {
            throw new EntityNotFoundException("Uno o más roles no existen");
        }

        return roles;
    }

    private Role find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Rol no encontrado"));
    }

    private RoleDtos.RoleResponse toResponse(Role role) {
        return new RoleDtos.RoleResponse(
                role.id,
                role.name,
                role.description,
                role.systemRole,
                Set.copyOf(role.permissions),
                repository.countActiveUsersByRoleId(role.id)
        );
    }

    private String normalizeName(String value) {
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
