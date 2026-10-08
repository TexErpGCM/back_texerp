package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.AppUser;
import co.texerp.integrations.domain.Role;
import co.texerp.integrations.dto.AdminDtos.UserCounters;
import co.texerp.integrations.dto.AdminDtos.UserManagement;
import co.texerp.integrations.dto.AdminDtos.UserPage;
import co.texerp.integrations.dto.AdminDtos.UserResponse;
import co.texerp.integrations.dto.Requests.UserRequest;
import co.texerp.integrations.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;

@Service
public class UserManagementService {
    private final UserRepository repository;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;

    public UserManagementService(
            UserRepository repository,
            AuditService audit,
            PasswordEncoder passwordEncoder,
            RoleService roleService
    ) {
        this.repository = repository;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
    }

    @Transactional(readOnly = true)
    public UserManagement getManagement(String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 5), 100);
        var users = repository.selectPage(normalize(search), PageRequest.of(safePage, safeSize));

        UserCounters counters = new UserCounters(
                repository.selectCount(),
                repository.selectActiveCount(),
                repository.selectActiveCountByRoleName("ADMINISTRADOR"),
                repository.selectActiveCountByRoleName("ANALISTA")
        );

        return new UserManagement(
                counters,
                new UserPage(
                        users.getContent().stream().map(this::toResponse).toList(),
                        users.getNumber(),
                        users.getSize(),
                        users.getTotalElements(),
                        users.getTotalPages()
                ),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public UserResponse create(UserRequest request, HttpServletRequest http) {
        validateUniqueEmail(request.email(), null);
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("La contraseña temporal es obligatoria");
        }

        AppUser user = new AppUser();
        user.name = request.name().trim();
        user.email = normalizeEmail(request.email());
        user.username = user.email.substring(0, user.email.indexOf('@'));
        user.password = passwordEncoder.encode(request.password());
        user.roles = new LinkedHashSet<>(roleService.requireRoles(request.roleIds()));
        user.active = request.active();

        AppUser saved = repository.save(user);
        audit.log("CREATE", "AppUser", saved.id, "Usuario creado con roles " + roleNames(saved), http);
        return toResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest request, HttpServletRequest http) {
        AppUser user = find(id);
        validateUniqueEmail(request.email(), id);

        var newRoles = roleService.requireRoles(request.roleIds());
        validateProtectedAdministratorChange(user, newRoles, request.active());

        user.name = request.name().trim();
        user.email = normalizeEmail(request.email());
        if (request.password() != null && !request.password().isBlank()) {
            user.password = passwordEncoder.encode(request.password());
        }
        user.roles.clear();
        user.roles.addAll(newRoles);
        user.active = request.active();

        AppUser saved = repository.save(user);
        audit.log(
                "UPDATE",
                "AppUser",
                saved.id,
                "Usuario actualizado con roles " + roleNames(saved) + " y estado " + (saved.active ? "activo" : "inactivo"),
                http
        );
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest http) {
        AppUser user = find(id);
        if (isCurrentUser(user)) {
            throw new BusinessConflictException("No puedes eliminar tu propio usuario mientras tienes la sesión activa");
        }
        if (user.active && hasRole(user, "ADMINISTRADOR")
                && repository.selectActiveCountByRoleName("ADMINISTRADOR") <= 1) {
            throw new BusinessConflictException("Debe permanecer al menos un administrador activo");
        }
        repository.deleteByIdStatement(id);
        audit.log("DELETE", "AppUser", id, "Usuario eliminado: " + user.email, http);
    }

    private void validateProtectedAdministratorChange(AppUser user, java.util.Set<Role> roles, boolean active) {
        boolean removesActiveAdmin = hasRole(user, "ADMINISTRADOR")
                && user.active
                && (!active || roles.stream().noneMatch(role -> role.name.equalsIgnoreCase("ADMINISTRADOR")));

        if (removesActiveAdmin && repository.selectActiveCountByRoleName("ADMINISTRADOR") <= 1) {
            throw new BusinessConflictException("Debe permanecer al menos un administrador activo");
        }

        if (isCurrentUser(user) && removesActiveAdmin) {
            throw new BusinessConflictException("No puedes desactivar ni retirar tu propio rol de administrador");
        }
    }

    private void validateUniqueEmail(String email, Long excludeId) {
        if (repository.selectEmailCount(normalizeEmail(email), excludeId) > 0) {
            throw new BusinessConflictException("Ya existe un usuario con ese correo electrónico");
        }
    }

    private AppUser find(Long id) {
        return repository.selectById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
    }

    private boolean isCurrentUser(AppUser user) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getName() != null
                && authentication.getName().equalsIgnoreCase(user.email);
    }

    private boolean hasRole(AppUser user, String name) {
        return user.roles.stream().anyMatch(role -> role.name.equalsIgnoreCase(name));
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.id,
                user.createdAt,
                user.updatedAt,
                user.lastLoginAt,
                user.name,
                user.email,
                roleNames(user),
                user.active
        );
    }

    private String roleNames(AppUser user) {
        return String.join(", ", user.roles.stream().map(role -> role.name).sorted().toList());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
