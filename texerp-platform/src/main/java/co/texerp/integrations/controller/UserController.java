package co.texerp.integrations.controller;

import co.texerp.integrations.domain.AppUser;
import co.texerp.integrations.domain.Role;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.exception.UserConflictException;
import co.texerp.integrations.repository.UserRepository;
import co.texerp.integrations.service.AuditService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserController(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<AppUser>> create(
            @Valid @RequestBody CreateUserRequest request
    ) {

        String username =
                request.username()
                        .trim()
                        .toLowerCase();

        String email =
                request.email()
                        .trim()
                        .toLowerCase();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new UserConflictException(
                    "username",
                    "El nombre de usuario ya se encuentra registrado"
            );
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new UserConflictException(
                    "email",
                    "El correo ya se encuentra registrado"
            );
        }
        AppUser user = new AppUser();

        user.name = request.name().trim();
        user.username = username;
        user.email = email;
        user.password = passwordEncoder.encode(
                request.password()
        );
        user.role = request.role();
        user.active = request.active();

        AppUser saved =
                users.save(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.ok(
                                "Usuario creado correctamente",
                                saved
                        )
                );
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<AppUser>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            HttpServletRequest httpRequest
    ) {

        AppUser user =
                users.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Usuario no encontrado"
                                )
                        );

        String username =
                request.username()
                        .trim()
                        .toLowerCase();

        String email =
                request.email()
                        .trim()
                        .toLowerCase();
        String previousName =
                user.name;

        String previousUsername =
                user.username;

        String previousEmail =
                user.email;

        Role previousRole =
                user.role;

        if (users.existsByUsernameIgnoreCaseAndIdNot(
                username,
                id
        )) {

            throw new UserConflictException(
                    "username",
                    "El nombre de usuario ya se encuentra registrado"
            );
        }

        if (users.existsByEmailIgnoreCaseAndIdNot(
                email,
                id
        )) {

            throw new UserConflictException(
                    "email",
                    "El correo ya se encuentra registrado"
            );
        }

        user.name =
                request.name().trim();

        user.username =
                username;

        user.email =
                email;

        user.role =
                request.role();

        AppUser saved =
                users.save(user);
        String details =
                "Usuario actualizado. "
                        + "name: "
                        + previousName
                        + " -> "
                        + saved.name

                        + ", username: "
                        + previousUsername
                        + " -> "
                        + saved.username

                        + ", email: "
                        + previousEmail
                        + " -> "
                        + saved.email

                        + ", role: "
                        + previousRole
                        + " -> "
                        + saved.role;

        auditService.log(
                "UPDATE_USER",
                "AppUser",
                id,
                details,
                httpRequest
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Usuario actualizado correctamente",
                        saved
                )
        );
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<ApiResponse<AppUser>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            HttpServletRequest httpRequest
    ) {

        AppUser user =
                users.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Usuario no encontrado"
                                )
                        );

        boolean previousStatus =
                user.active;

        user.active =
                request.active();

        AppUser saved =
                users.save(user);

        String action =
                saved.active
                        ? "ACTIVATE_USER"
                        : "DEACTIVATE_USER";

        String details =
                "Estado del usuario actualizado. "
                        + "active: "
                        + previousStatus
                        + " -> "
                        + saved.active;

        auditService.log(
                action,
                "AppUser",
                id,
                details,
                httpRequest
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        saved.active
                                ? "Usuario activado correctamente"
                                : "Usuario desactivado correctamente",
                        saved
                )
        );
    }

    @GetMapping
    @Transactional
    public ResponseEntity<ApiResponse<Page<UserResponse>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active
    ) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "La página no puede ser negativa"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "El tamaño de página debe estar entre 1 y 100"
            );
        }

        Specification<AppUser> specification =
                (root, query, criteriaBuilder) -> {

                    ArrayList<Predicate> predicates =
                            new ArrayList<>();
                    if (name != null && !name.isBlank()) {

                        predicates.add(
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                root.get("name")
                                        ),
                                        "%"
                                                + name.trim().toLowerCase()
                                                + "%"
                                )
                        );
                    }
                    if (email != null && !email.isBlank()) {

                        predicates.add(
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                root.get("email")
                                        ),
                                        "%"
                                                + email.trim().toLowerCase()
                                                + "%"
                                )
                        );
                    }
                    if (role != null) {

                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("role"),
                                        role
                                )
                        );
                    }

                    if (active != null) {

                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("active"),
                                        active
                                )
                        );
                    }

                    return criteriaBuilder.and(
                            predicates.toArray(
                                    new Predicate[0]
                            )
                    );
                };
        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.ASC,
                                "name"
                        )
                );
        Page<UserResponse> result =
                users.findAll(
                                specification,
                                pageable
                        )
                        .map(user ->
                                new UserResponse(
                                        user.id,
                                        user.getName(),
                                        user.getUsername(),
                                        user.getEmail(),
                                        user.getRole(),
                                        user.isActive()
                                )
                        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Usuarios consultados correctamente",
                        result
                )
        );
    }
    public record CreateUserRequest(

            @NotBlank(
                    message = "El nombre es obligatorio"
            )
            String name,

            @NotBlank(
                    message = "El nombre de usuario es obligatorio"
            )
            String username,

            @NotBlank(
                    message = "El correo es obligatorio"
            )
            @Email(
                    message = "El correo no tiene un formato válido"
            )
            String email,

            @NotBlank(
                    message = "La contraseña es obligatoria"
            )
            String password,

            @NotNull(
                    message = "El rol es obligatorio"
            )
            Role role,

            @NotNull(
                    message = "El estado es obligatorio"
            )
            Boolean active
    ) {
    }

    public record UpdateUserRequest(

            @NotBlank(
                    message = "El nombre es obligatorio"
            )
            String name,

            @NotBlank(
                    message = "El nombre de usuario es obligatorio"
            )
            String username,

            @NotBlank(
                    message = "El correo es obligatorio"
            )
            @Email(
                    message = "El correo no tiene un formato válido"
            )
            String email,

            @NotNull(
                    message = "El rol es obligatorio"
            )
            Role role
    ) {
    }
    public record UpdateStatusRequest(

            @NotNull(
                    message = "El estado es obligatorio"
            )
            Boolean active
    ) {
    }
    public record UserResponse(
            Long id,
            String name,
            String username,
            String email,
            Role role,
            boolean active
    ) {
    }
}