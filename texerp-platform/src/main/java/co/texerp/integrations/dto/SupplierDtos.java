package co.texerp.integrations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SupplierDtos {
    private SupplierDtos() {}

    /**
     * Datos editables del proveedor. El estado se administra por el endpoint
     * de cambio de estado para garantizar que todo proveedor nuevo nazca activo.
     */
    public record SupplierRequest(
            @NotBlank(message = "La identificación tributaria es obligatoria")
            @Size(max = 50, message = "La identificación tributaria debe tener máximo 50 caracteres")
            String taxId,

            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 150, message = "El nombre debe tener máximo 150 caracteres")
            String name,

            @NotBlank(message = "El teléfono es obligatorio")
            @Size(max = 30, message = "El teléfono debe tener máximo 30 caracteres")
            String phone,

            @NotBlank(message = "El correo electrónico es obligatorio")
            @Email(message = "El correo electrónico no tiene un formato válido")
            @Size(max = 150, message = "El correo electrónico debe tener máximo 150 caracteres")
            String email
    ) {}

    public record SupplierStatusRequest(
            @NotNull(message = "El estado es obligatorio")
            Boolean active
    ) {}

    public record SupplierResponse(
            Long id,
            String taxId,
            String name,
            String phone,
            String email,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record SupplierPage(
            List<SupplierResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
