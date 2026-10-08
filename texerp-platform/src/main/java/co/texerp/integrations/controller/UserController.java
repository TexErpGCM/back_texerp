package co.texerp.integrations.controller;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.AppUser;
import co.texerp.integrations.domain.Role;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.RoleDtos;
import co.texerp.integrations.exception.UserConflictException;
import co.texerp.integrations.repository.UserRepository;
import co.texerp.integrations.service.AuditService;
import co.texerp.integrations.service.RoleService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final RoleService roleService;

    public UserController(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            RoleService roleService
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.roleService = roleService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest
    ) {
        String username = normalize(request.username());
        String email = normalize(request.email());

        validateUnique(username, email, null);

        AppUser user = new AppUser();
        user.name = request.name().trim();
        user.username = username;
        user.email = email;
        user.password = passwordEncoder.encode(request.password());
        user.roles = new LinkedHashSet<>(roleService.requireRoles(request.roleIds()));
        user.active = request.active();

        AppUser saved = users.saveAndFlush(user);

        auditService.log(
                "CREATE_USER",
                "AppUser",
                saved.id,
                "Usuario creado. Roles: " + roleNames(saved),
                httpRequest
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario creado correctamente", toResponse(saved)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            HttpServletRequest httpRequest
    ) {
        AppUser user = find(id);
        String username = normalize(request.username());
        String email = normalize(request.email());
        validateUnique(username, email, id);

        Set<Role> newRoles = roleService.requireRoles(request.roleIds());
        validateAdministratorProtection(user, newRoles, user.active);

        String previousRoles = roleNames(user);
        String previousName = user.name;
        String previousEmail = user.email;

        user.name = request.name().trim();
        user.username = username;
        user.email = email;
        user.roles.clear();
        user.roles.addAll(newRoles);

        AppUser saved = users.saveAndFlush(user);

        auditService.log(
                "UPDATE_USER",
                "AppUser",
                id,
                "Usuario actualizado. name: " + previousName + " -> " + saved.name
                        + ", email: " + previousEmail + " -> " + saved.email
                        + ", roles: " + previousRoles + " -> " + roleNames(saved),
                httpRequest
        );

        return ApiResponse.ok("Usuario actualizado correctamente", toResponse(saved));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ApiResponse<UserResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        AppUser user = find(id);
        validateAdministratorProtection(user, user.roles, request.active());

        boolean previousStatus = user.active;
        user.active = request.active();
        AppUser saved = users.saveAndFlush(user);

        auditService.log(
                saved.active ? "ACTIVATE_USER" : "DEACTIVATE_USER",
                "AppUser",
                id,
                "Estado actualizado: " + previousStatus + " -> " + saved.active,
                httpRequest
        );

        return ApiResponse.ok(
                saved.active ? "Usuario activado correctamente" : "Usuario desactivado correctamente",
                toResponse(saved)
        );
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Page<UserResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active
    ) {
        if (page < 0) throw new IllegalArgumentException("La página no puede ser negativa");
        if (size < 1 || size > 100) throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 100");

        Specification<AppUser> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (email != null && !email.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + email.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (role != null && !role.isBlank()) {
                var join = root.join("roles", JoinType.INNER);
                predicates.add(cb.equal(cb.upper(join.get("name")), role.trim().toUpperCase(Locale.ROOT)));
                query.distinct(true);
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<UserResponse> result = users.findAll(specification, pageable).map(this::toResponse);

        return ApiResponse.ok("Usuarios consultados correctamente", result);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<UserResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok("Usuario consultado correctamente", toResponse(find(id)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        AppUser user = find(id);

        if (isCurrentUser(user)) {
            throw new BusinessConflictException("No puedes eliminar tu propio usuario mientras tienes la sesión activa");
        }

        if (isActiveAdministrator(user) && users.selectActiveCountByRoleName("ADMINISTRADOR") <= 1) {
            throw new BusinessConflictException("Debe permanecer al menos un administrador activo");
        }

        users.delete(user);
        auditService.log("DELETE_USER", "AppUser", id, "Usuario eliminado: " + user.email, httpRequest);

        return ApiResponse.ok("Usuario eliminado correctamente", null);
    }

    private void validateUnique(String username, String email, Long excludeId) {
        boolean usernameExists = excludeId == null
                ? users.existsByUsernameIgnoreCase(username)
                : users.existsByUsernameIgnoreCaseAndIdNot(username, excludeId);
        boolean emailExists = excludeId == null
                ? users.existsByEmailIgnoreCase(email)
                : users.existsByEmailIgnoreCaseAndIdNot(email, excludeId);

        if (usernameExists) throw new UserConflictException("username", "El nombre de usuario ya se encuentra registrado");
        if (emailExists) throw new UserConflictException("email", "El correo ya se encuentra registrado");
    }

    private void validateAdministratorProtection(AppUser user, Set<Role> requestedRoles, boolean requestedActive) {
        boolean wasAdmin = hasRole(user.roles, "ADMINISTRADOR") && user.active;
        boolean remainsAdmin = hasRole(requestedRoles, "ADMINISTRADOR") && requestedActive;

        if (wasAdmin && !remainsAdmin && users.selectActiveCountByRoleName("ADMINISTRADOR") <= 1) {
            throw new BusinessConflictException("Debe permanecer al menos un administrador activo");
        }

        if (isCurrentUser(user) && wasAdmin && !remainsAdmin) {
            throw new BusinessConflictException("No puedes retirar tu propio rol de administrador ni desactivar tu usuario");
        }
    }

    private boolean isCurrentUser(AppUser user) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null && auth.getName().equalsIgnoreCase(user.email);
    }

    private boolean isActiveAdministrator(AppUser user) {
        return user.active && hasRole(user.roles, "ADMINISTRADOR");
    }

    private boolean hasRole(Set<Role> roles, String name) {
        return roles.stream().anyMatch(role -> role.name.equalsIgnoreCase(name));
    }

    private AppUser find(Long id) {
        return users.selectById(id).orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
    }

    private UserResponse toResponse(AppUser user) {
        List<RoleDtos.RoleSummary> roles = user.roles.stream()
                .sorted(Comparator.comparing(role -> role.name))
                .map(role -> new RoleDtos.RoleSummary(role.id, role.name, role.description, role.systemRole))
                .toList();

        String primaryRole = roles.stream().map(RoleDtos.RoleSummary::name).findFirst().orElse(null);

        return new UserResponse(
                user.id,
                user.name,
                user.username,
                user.email,
                primaryRole,
                roles,
                user.active
        );
    }

    private String roleNames(AppUser user) {
        return user.roles.stream().map(role -> role.name).sorted().toList().toString();
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record CreateUserRequest(
            @NotBlank(message = "El nombre es obligatorio") String name,
            @NotBlank(message = "El nombre de usuario es obligatorio") String username,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no tiene un formato válido") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password,
            @NotEmpty(message = "Debe asignarse al menos un rol") Set<Long> roleIds,
            @NotNull(message = "El estado es obligatorio") Boolean active
    ) {}

    public record UpdateUserRequest(
            @NotBlank(message = "El nombre es obligatorio") String name,
            @NotBlank(message = "El nombre de usuario es obligatorio") String username,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no tiene un formato válido") String email,
            @NotEmpty(message = "Debe asignarse al menos un rol") Set<Long> roleIds
    ) {}

    public record UpdateStatusRequest(
            @NotNull(message = "El estado es obligatorio") Boolean active
    ) {}

    public record UserResponse(
            Long id,
            String name,
            String username,
            String email,
            String role,
            List<RoleDtos.RoleSummary> roles,
            boolean active
    ) {}
}
