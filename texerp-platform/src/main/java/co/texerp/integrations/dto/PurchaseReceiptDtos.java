package co.texerp.integrations.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PurchaseReceiptDtos {
    public record LineRequest(@NotNull Long orderLineId, @NotNull @DecimalMin("0.001") BigDecimal quantity) {}
    public record CreateRequest(@NotNull Long warehouseId, @NotBlank String document,
                                @NotEmpty List<@Valid LineRequest> lines) {}
    public record LineResponse(Long id, Long orderLineId, Long variantId, BigDecimal quantity,
                               BigDecimal unitCost, BigDecimal total, Long movementId) {}
    public record Response(Long id, String number, Instant receivedAt, Long purchaseOrderId,
                           Long warehouseId, String document, String receivedBy, List<LineResponse> lines) {}
}
