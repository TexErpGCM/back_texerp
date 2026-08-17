package co.texerp.integrations.controller;

import co.texerp.integrations.domain.AppUser;
import co.texerp.integrations.domain.Role;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserController(
            UserRepository users,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    @Transactional
    public ApiResponse<AppUser> create(
            @Valid @RequestBody CreateUserRequest request
    ) {

        if (users.selectEmailCount(request.email(), null) > 0) {
            throw new IllegalArgumentException("El correo ya se encuentra registrado");
        }

        AppUser user = new AppUser();

        user.name = request.name();
        user.email = request.email().trim().toLowerCase();
        user.password = passwordEncoder.encode(request.password());
        user.role = request.role();
        user.active = true;

        AppUser saved = users.save(user);

        return ApiResponse.ok(
                "Usuario creado correctamente",
                saved
        );
    }

    public record CreateUserRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotNull Role role
    ) {}
}