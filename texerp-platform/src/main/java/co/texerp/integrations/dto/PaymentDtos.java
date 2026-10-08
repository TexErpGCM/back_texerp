package co.texerp.integrations.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentCreateRequest(
            @NotBlank(message = "El método de pago es obligatorio")
            @Size(max = 50, message = "El método de pago no puede superar 50 caracteres") String method,
            @NotNull(message = "El monto es obligatorio") BigDecimal amount,
            Instant paidAt,
            @Size(max = 150, message = "La referencia no puede superar 150 caracteres") String reference
    ) {}
}
