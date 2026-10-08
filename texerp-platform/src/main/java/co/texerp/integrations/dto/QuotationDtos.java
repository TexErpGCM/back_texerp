package co.texerp.integrations.dto;

import co.texerp.integrations.domain.QuotationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class QuotationDtos {
    private QuotationDtos() {}

    public record QuotationLineRequest(
            @NotNull(message = "La variante es obligatoria") Long variantId,
            @NotNull(message = "La cantidad es obligatoria") BigDecimal quantity,
            @NotNull(message = "El precio unitario es obligatorio")
            @DecimalMin(value = "0.0", inclusive = true, message = "El precio unitario no puede ser negativo") BigDecimal unitPrice
    ) {}

    public record QuotationCreateRequest(
            @NotNull(message = "El cliente es obligatorio") Long customerId,
            @NotNull(message = "La fecha de emisión es obligatoria") LocalDate issueDate,
            @NotNull(message = "La fecha de vencimiento es obligatoria") LocalDate expirationDate,
            @Size(max = 1000, message = "Las condiciones no pueden superar 1000 caracteres") String conditions,
            @NotEmpty(message = "La cotización debe contener al menos una línea") List<@Valid QuotationLineRequest> lines
    ) {}

    public record QuotationLineResponse(
            Long id, Long variantId, String sku, String product,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal subtotal
    ) {}

    public record QuotationResponse(
            Long id, String number, Long customerId, String customerDocument, String customerName,
            LocalDate issueDate, LocalDate expirationDate, QuotationStatus status,
            boolean expired, String conditions, BigDecimal subtotal, BigDecimal total,
            Long convertedSaleId, String convertedSaleNumber,
            List<QuotationLineResponse> lines
    ) {}

    public record ConvertQuotationRequest(
            @NotNull(message = "La bodega es obligatoria") Long warehouseId
    ) {}
}
