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
import java.util.Locale;

@Service
public class UserManagementService {
    private final UserRepository repository;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository repository, AuditService audit, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserManagement getManagement(String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 5), 100);
        var users = repository.selectPage(normalize(search), PageRequest.of(safePage, safeSize));
        var aggregate = repository.selectAggregate();
        UserCounters counters = aggregate == null
                ? new UserCounters(0, 0, 0, 0)
                : new UserCounters(
                safeLong(aggregate.getTotal()),
                safeLong(aggregate.getActive()),
                safeLong(aggregate.getAdministrators()),
                safeLong(aggregate.getAnalysts())
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
        apply(user, request, true);
        AppUser saved = repository.save(user);
        audit.log("CREATE", "AppUser", saved.id,
                "Usuario creado con rol " + saved.role.name(), http);
        return toResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest request, HttpServletRequest http) {
        AppUser user = find(id);
        validateUniqueEmail(request.email(), id);
        validateProtectedAdministratorChange(user, request);
        apply(user, request, false);
        AppUser saved = repository.save(user);
        audit.log("UPDATE", "AppUser", saved.id,
                "Usuario actualizado con rol " + saved.role.name() + " y estado " + (saved.active ? "activo" : "inactivo"),
                http);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest http) {
        AppUser user = find(id);
        if (isCurrentUser(user)) {
            throw new BusinessConflictException("No puedes eliminar tu propio usuario mientras tienes la sesión activa");
        }
        if (user.role == Role.ADMINISTRADOR && user.active
                && repository.selectActiveCountByRole(Role.ADMINISTRADOR) <= 1) {
            throw new BusinessConflictException("Debe permanecer al menos un administrador activo");
        }
        repository.deleteByIdStatement(id);
        audit.log("DELETE", "AppUser", id, "Usuario eliminado: " + user.email, http);
    }

    private void apply(AppUser user, UserRequest request, boolean passwordRequired) {
        user.name = request.name().trim();
        user.email = normalizeEmail(request.email());
        if (request.password() != null && !request.password().isBlank()) {
            user.password = passwordEncoder.encode(request.password());
        } else if (passwordRequired) {
            throw new IllegalArgumentException("La contraseña temporal es obligatoria");
        }
        user.role = request.role();
        user.active = request.active();
    }

    private void validateProtectedAdministratorChange(AppUser user, UserRequest request) {
        boolean removesActiveAdmin = user.role == Role.ADMINISTRADOR
                && user.active
                && (request.role() != Role.ADMINISTRADOR || !Boolean.TRUE.equals(request.active()));
        if (removesActiveAdmin && repository.selectActiveCountByRole(Role.ADMINISTRADOR) <= 1) {
            throw new BusinessConflictException("Debe permanecer al menos un administrador activo");
        }
        if (isCurrentUser(user)
                && (request.role() != Role.ADMINISTRADOR || !Boolean.TRUE.equals(request.active()))) {
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

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.id,
                user.createdAt,
                user.updatedAt,
                user.lastLoginAt,
                user.name,
                user.email,
                user.role == null ? null : user.role.name(),
                user.active
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
