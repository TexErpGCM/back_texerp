package co.texerp.integrations.dto;

import co.texerp.integrations.domain.PurchaseOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PurchaseOrderDtos {
    public record LineRequest(@NotNull Long variantId, @NotNull BigDecimal quantity,
                              @NotNull BigDecimal unitCost) {}
    public record CreateRequest(@NotNull Long supplierId,
                                @NotEmpty List<@Valid LineRequest> lines) {}
    public record RejectRequest(@NotBlank(message = "El motivo es obligatorio") String reason) {}
    public record LineResponse(Long id, Long variantId, BigDecimal quantity,
                               BigDecimal receivedQuantity, BigDecimal unitCost, BigDecimal total) {}
    public record Response(Long id, String number, Long supplierId, Instant orderedAt,
                           PurchaseOrderStatus status, BigDecimal total, Instant decidedAt,
                           String decidedBy, String rejectionReason, List<LineResponse> lines) {}
}
