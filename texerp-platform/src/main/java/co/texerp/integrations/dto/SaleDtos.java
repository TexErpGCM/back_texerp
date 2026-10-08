package co.texerp.integrations.dto;

import co.texerp.integrations.domain.SaleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SaleDtos {
    private SaleDtos() {}

    public record SaleLineRequest(
            @NotNull(message = "La variante es obligatoria") Long variantId,
            @NotNull(message = "La cantidad es obligatoria") BigDecimal quantity,
            BigDecimal unitPrice,
            @DecimalMin(value = "0.0", message = "El descuento no puede ser negativo") BigDecimal discountAmount,
            @DecimalMin(value = "0.0", message = "El impuesto no puede ser negativo") BigDecimal taxRate
    ) {}

    public record SaleCreateRequest(
            @NotNull(message = "El cliente es obligatorio") Long customerId,
            @NotNull(message = "La bodega es obligatoria") Long warehouseId,
            @NotEmpty(message = "La venta debe contener al menos una línea") List<@Valid SaleLineRequest> lines
    ) {}

    public record SaleLineResponse(
            Long id, Long variantId, String sku, String product,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitCost,
            BigDecimal discountAmount, BigDecimal taxRate, BigDecimal taxAmount,
            BigDecimal subtotal, BigDecimal total
    ) {}

    public record PaymentResponse(
            Long id, String method, BigDecimal amount, Instant paidAt, String reference
    ) {}

    public record SaleResponse(
            Long id, String number, SaleStatus status, Instant confirmedAt,
            Long customerId, String customerDocument, String customerName,
            Long warehouseId, String warehouseCode, String warehouseName,
            Long quotationId, String quotationNumber,
            BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total,
            BigDecimal totalPaid, BigDecimal pendingBalance,
            List<SaleLineResponse> lines, List<PaymentResponse> payments
    ) {}
}
