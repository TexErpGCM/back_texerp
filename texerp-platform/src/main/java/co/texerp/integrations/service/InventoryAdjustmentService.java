package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.dto.InventoryAdjustmentDtos.InventoryAdjustmentRequest;
import co.texerp.integrations.dto.InventoryAdjustmentDtos.InventoryAdjustmentResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class InventoryAdjustmentService {

    private static final String NEGATIVE_ADJUST_PERMISSION = "INVENTORY_NEGATIVE_ADJUST";

    private final InventoryBalanceUpdater balanceUpdater;

    public InventoryAdjustmentService(InventoryBalanceUpdater balanceUpdater) {
        this.balanceUpdater = balanceUpdater;
    }

    /**
     * Registra un ajuste manual de inventario.
     *
     * El balance y el movimiento de auditoría se persisten en la misma
     * transacción. Cualquier error provoca rollback completo.
     */
    @Transactional
    public InventoryAdjustmentResponse create(InventoryAdjustmentRequest request) {
        BigDecimal quantity = requireNonZeroQuantity(request.quantity());
        String reason = normalizeReason(request.reason());
        String sourceDocument = normalizeOrGenerateDocument(request.sourceDocument());
        boolean mayLeaveNegative = hasAuthority(NEGATIVE_ADJUST_PERMISSION);

        var result = balanceUpdater.applyInventoryAdjustment(
                request.variantId(),
                request.warehouseId(),
                quantity,
                sourceDocument,
                reason,
                mayLeaveNegative
        );

        var balance = result.balance();
        var movement = result.movement();

        return new InventoryAdjustmentResponse(
                movement.id,
                balance.id,
                movement.variant.id,
                movement.variant.sku,
                movement.warehouse.id,
                movement.warehouse.code,
                movement.quantity,
                movement.previousAvailable,
                movement.newAvailable,
                movement.newReserved,
                movement.sourceDocument,
                movement.reason,
                movement.performedBy,
                movement.movementAt
        );
    }

    private BigDecimal requireNonZeroQuantity(BigDecimal quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("La cantidad del ajuste es obligatoria");
        }
        if (quantity.signum() == 0) {
            throw new IllegalArgumentException("La cantidad del ajuste debe ser diferente de cero");
        }
        return quantity;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo del ajuste es obligatorio");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("El motivo del ajuste no puede superar 500 caracteres");
        }
        return normalized;
    }

    private String normalizeOrGenerateDocument(String sourceDocument) {
        if (sourceDocument == null || sourceDocument.isBlank()) {
            return "ADJ-" + UUID.randomUUID();
        }
        String normalized = sourceDocument.trim();
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("El documento origen no puede superar 255 caracteres");
        }
        return normalized;
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessConflictException("No existe un usuario autenticado para registrar el ajuste");
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
