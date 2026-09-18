package co.texerp.integrations.dto;

import co.texerp.integrations.domain.InventoryMovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InventoryMovementDtos {

    private InventoryMovementDtos() {
    }

    public record InventoryMovementResponse(
            Long id,
            Long variantId,
            String sku,
            Long productId,
            String productCode,
            String productName,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            InventoryMovementType type,
            BigDecimal quantity,
            BigDecimal reservedDelta,
            BigDecimal previousAvailable,
            BigDecimal newAvailable,
            BigDecimal previousReserved,
            BigDecimal newReserved,
            String sourceDocument,
            String performedBy,
            Instant movementAt,
            String reason,
            Long compensatesMovementId
    ) {
    }

    public record InventoryMovementPage(
            List<InventoryMovementResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record CompensationRequest(
            @NotBlank(message = "es obligatorio")
            @Size(max = 255, message = "no puede superar 255 caracteres")
            String sourceDocument,

            @NotBlank(message = "es obligatorio")
            @Size(max = 500, message = "no puede superar 500 caracteres")
            String reason
    ) {
    }
}
