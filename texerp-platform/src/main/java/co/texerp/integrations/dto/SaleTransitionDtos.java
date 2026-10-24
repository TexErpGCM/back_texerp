package co.texerp.integrations.dto;

import jakarta.validation.constraints.NotBlank;

public class SaleTransitionDtos {
    public record DispatchRequest(String evidence) {}
    public record CancelRequest(@NotBlank(message = "El motivo es obligatorio") String reason) {}
}
