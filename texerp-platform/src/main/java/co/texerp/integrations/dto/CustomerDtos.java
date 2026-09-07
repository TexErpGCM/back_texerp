package co.texerp.integrations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record CustomerRequest(
            @NotBlank(message = "El documento es obligatorio")
            @Size(max = 50, message = "El documento debe tener máximo 50 caracteres")
            String document,

            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 150, message = "El nombre debe tener máximo 150 caracteres")
            String name,

            @NotBlank(message = "El tipo es obligatorio")
            @Size(max = 50, message = "El tipo debe tener máximo 50 caracteres")
            String type,

            @Email(message = "El correo electrónico no tiene un formato válido")
            @Size(max = 150, message = "El correo electrónico debe tener máximo 150 caracteres")
            String email,

            @Size(max = 30, message = "El teléfono debe tener máximo 30 caracteres")
            String phone,

            @Size(max = 80, message = "La clasificación debe tener máximo 80 caracteres")
            String classification,

            @NotNull(message = "El estado es obligatorio")
            Boolean active
    ) {}

    public record CustomerStatusRequest(
            @NotNull(message = "El estado es obligatorio")
            Boolean active
    ) {}

    public record CustomerResponse(
            Long id,
            String document,
            String name,
            String type,
            String email,
            String phone,
            String classification,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CustomerPage(
            List<CustomerResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
