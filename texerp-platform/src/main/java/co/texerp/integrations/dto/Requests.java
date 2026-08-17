package co.texerp.integrations.dto;

import co.texerp.integrations.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class Requests {

    private Requests() {
    }

    public record UserRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
            String name,

            @NotBlank(message = "El correo electrónico es obligatorio")
            @Email(message = "El correo electrónico no es válido")
            @Size(max = 255, message = "El correo electrónico no puede superar 255 caracteres")
            String email,

            String password,

            @NotNull(message = "El rol es obligatorio")
            Role role,

            boolean active
    ) {
    }
}
