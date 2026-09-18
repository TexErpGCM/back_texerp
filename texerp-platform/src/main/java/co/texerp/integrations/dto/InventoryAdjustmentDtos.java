package co.texerp.integrations.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class InventoryAdjustmentDtos {

    private InventoryAdjustmentDtos() {
    }

    public record InventoryAdjustmentRequest(
            @NotNull(message = "La variante es obligatoria")
            Long variantId,

            @NotNull(message = "La bodega es obligatoria")
            Long warehouseId,

            @NotNull(message = "La cantidad del ajuste es obligatoria")
            @Digits(integer = 16, fraction = 3, message = "La cantidad admite máximo 16 enteros y 3 decimales")
            BigDecimal quantity,

            @NotBlank(message = "El motivo del ajuste es obligatorio")
            @Size(max = 500, message = "El motivo no puede superar 500 caracteres")
            String reason,

            @Size(max = 255, message = "El documento origen no puede superar 255 caracteres")
            String sourceDocument
    ) {
    }

    public record InventoryAdjustmentResponse(
            Long movementId,
            Long balanceId,
            Long variantId,
            String sku,
            Long warehouseId,
            String warehouseCode,
            BigDecimal quantity,
            BigDecimal previousAvailable,
            BigDecimal newAvailable,
            BigDecimal reserved,
            String sourceDocument,
            String reason,
            String performedBy,
            Instant movementAt
    ) {
    }
}
