package co.texerp.integrations.dto;

import co.texerp.integrations.domain.Permission;
import co.texerp.integrations.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(

            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato válido")
            String email,

            @NotBlank(message = "La contraseña es obligatoria")
            String password
    ) {
    }

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Instant refreshTokenExpiresAt,
            UserSession user
    ) {
    }

    public record UserSession(
            Long id,
            String name,
            String email,
            Role role,
            Set<Permission> permissions
    ) {
    }

    public record RefreshRequest(
            @NotBlank(message = "El refresh token es obligatorio")
            String refreshToken
    ) {
    }

    public record RefreshResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Instant refreshTokenExpiresAt
    ) {
    }
}