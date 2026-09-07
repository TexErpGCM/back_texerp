package co.texerp.integrations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ProductDtos {
    private ProductDtos() {}

    public record ProductRequest(
            @NotBlank(message = "es obligatorio")
            @Size(max = 50, message = "debe tener máximo 50 caracteres") String code,
            @NotBlank(message = "es obligatorio")
            @Size(max = 150, message = "debe tener máximo 150 caracteres") String name,
            @NotBlank(message = "es obligatorio")
            @Size(max = 100, message = "debe tener máximo 100 caracteres") String fabricType,
            @NotBlank(message = "es obligatoria")
            @Size(max = 500, message = "debe tener máximo 500 caracteres") String composition,
            @NotNull(message = "es obligatorio") Boolean active
    ) {}

    public record ProductStatusRequest(
            @NotNull(message = "es obligatorio") Boolean active
    ) {}

    public record ProductResponse(
            Long id, String code, String name, String fabricType,
            String composition, boolean active, Instant createdAt, Instant updatedAt
    ) {}

    public record ProductPage(
            List<ProductResponse> content, int page, int size,
            long totalElements, int totalPages
    ) {}
}
