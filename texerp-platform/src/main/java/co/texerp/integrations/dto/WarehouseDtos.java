package co.texerp.integrations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class WarehouseDtos {
    private WarehouseDtos() {}

    public record WarehouseRequest(
            @NotBlank(message = "El código de la bodega es obligatorio")
            @Size(max = 50, message = "El código debe tener máximo 50 caracteres")
            String code,

            @NotBlank(message = "El nombre de la bodega es obligatorio")
            @Size(max = 150, message = "El nombre debe tener máximo 150 caracteres")
            String name,

            @NotBlank(message = "La ubicación de la bodega es obligatoria")
            @Size(max = 200, message = "La ubicación debe tener máximo 200 caracteres")
            String location
    ) {}

    public record WarehouseStatusRequest(
            @NotNull(message = "El estado es obligatorio")
            Boolean active
    ) {}

    public record WarehouseResponse(
            Long id,
            String code,
            String name,
            String location,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record WarehousePage(
            List<WarehouseResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
