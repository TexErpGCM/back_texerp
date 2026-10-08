package co.texerp.integrations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class Requests {

    private Requests() {
    }

    public record UserRequest(

            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
            String name,

            @NotBlank(message = "El nombre de usuario es obligatorio")
            @Size(max = 100, message = "El nombre de usuario no puede superar los 100 caracteres")
            String username,

            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato válido")
            @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
            String email,

            @Size(min = 4, max = 255, message = "La contraseña debe tener entre 4 y 255 caracteres")
            String password,

            @NotEmpty(message = "El usuario debe tener al menos un rol")
            Set<Long> roleIds,

            Boolean active
    ) {
    }
}