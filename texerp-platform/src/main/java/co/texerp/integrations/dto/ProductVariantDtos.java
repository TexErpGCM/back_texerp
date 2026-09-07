package co.texerp.integrations.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProductVariantDtos {
    private ProductVariantDtos() {}

    public record VariantRequest(
            @NotBlank(message = "es obligatorio")
            @Size(max = 60, message = "debe tener máximo 60 caracteres") String sku,
            @NotBlank(message = "es obligatorio")
            @Size(max = 80, message = "debe tener máximo 80 caracteres") String color,
            @Size(max = 120, message = "debe tener máximo 120 caracteres") String pattern,
            @NotNull(message = "es obligatorio")
            @DecimalMin(value = "0.0", message = "debe ser igual o mayor a cero") BigDecimal width,
            @NotBlank(message = "es obligatorio")
            @Size(max = 20, message = "debe tener máximo 20 caracteres") String unitCode,
            @NotNull(message = "es obligatorio")
            @DecimalMin(value = "0.0", message = "debe ser igual o mayor a cero") BigDecimal cost,
            @NotNull(message = "es obligatorio")
            @DecimalMin(value = "0.0", message = "debe ser igual o mayor a cero") BigDecimal salePrice,
            @NotNull(message = "es obligatorio") Boolean active
    ) {}

    public record PriceCostRequest(
            @NotNull(message = "es obligatorio")
            @DecimalMin(value = "0.0", message = "debe ser igual o mayor a cero") BigDecimal cost,
            @NotNull(message = "es obligatorio")
            @DecimalMin(value = "0.0", message = "debe ser igual o mayor a cero") BigDecimal salePrice
    ) {}

    public record VariantStatusRequest(@NotNull(message = "es obligatorio") Boolean active) {}

    public record VariantResponse(
            Long id, Long productId, String productCode, String sku, String color,
            String pattern, BigDecimal width, String unitCode, String unitName,
            BigDecimal cost, BigDecimal salePrice, boolean active,
            Instant createdAt, Instant updatedAt
    ) {}

    public record VariantPage(List<VariantResponse> content, int page, int size,
                              long totalElements, int totalPages) {}
}
