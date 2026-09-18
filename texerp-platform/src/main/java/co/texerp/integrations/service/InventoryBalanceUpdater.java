package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.InventoryBalance;
import co.texerp.integrations.domain.InventoryMovement;
import co.texerp.integrations.domain.InventoryMovementType;
import co.texerp.integrations.domain.ProductVariant;
import co.texerp.integrations.domain.Warehouse;
import co.texerp.integrations.repository.InventoryBalanceRepository;
import co.texerp.integrations.repository.InventoryMovementRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class InventoryBalanceUpdater {

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductVariantService variantService;
    private final WarehouseService warehouseService;

    public InventoryBalanceUpdater(
            InventoryBalanceRepository balanceRepository,
            InventoryMovementRepository movementRepository,
            ProductVariantService variantService,
            WarehouseService warehouseService
    ) {
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
        this.variantService = variantService;
        this.warehouseService = warehouseService;
    }

    /**
     * Aplica un movimiento confirmado y registra su trazabilidad dentro de la
     * misma transacción de negocio que confirma la venta, recepción o ajuste.
     *
     * Propagation.MANDATORY evita confirmar el balance por separado del proceso
     * que originó el movimiento.
     *
     * @param variantId variante afectada
     * @param warehouseId bodega afectada
     * @param type naturaleza del movimiento
     * @param availableDelta cambio firmado del saldo disponible
     * @param reservedDelta cambio firmado del saldo reservado
     * @param sourceDocument documento que originó el movimiento
     * @return balance y movimiento persistidos
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovementApplicationResult applyConfirmedMovement(
            Long variantId,
            Long warehouseId,
            InventoryMovementType type,
            BigDecimal availableDelta,
            BigDecimal reservedDelta,
            String sourceDocument
    ) {
        if (type == InventoryMovementType.COMPENSATION) {
            throw new IllegalArgumentException("Los movimientos compensatorios deben generarse a partir de un movimiento existente");
        }

        validateDocument(sourceDocument);
        BigDecimal safeAvailableDelta = valueOrZero(availableDelta);
        BigDecimal safeReservedDelta = valueOrZero(reservedDelta);
        validateMovementNature(type, safeAvailableDelta, safeReservedDelta);

        ProductVariant variant = variantService.requireActive(variantId);
        Warehouse warehouse = warehouseService.requireActive(warehouseId);

        return applyAndTrace(
                variant,
                warehouse,
                type,
                safeAvailableDelta,
                safeReservedDelta,
                sourceDocument.trim(),
                null,
                null
        );
    }

    /**
     * Revierte el impacto de un movimiento mediante un nuevo movimiento.
     * El registro original nunca se modifica ni se elimina.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovementApplicationResult applyCompensatingMovement(
            InventoryMovement original,
            String sourceDocument,
            String reason
    ) {
        if (original == null || original.id == null) {
            throw new IllegalArgumentException("El movimiento original es obligatorio");
        }
        validateDocument(sourceDocument);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo de la compensación es obligatorio");
        }
        if (reason.trim().length() > 500) {
            throw new IllegalArgumentException("El motivo de la compensación no puede superar 500 caracteres");
        }

        ProductVariant variant = variantService.requireActive(original.variant.id);
        Warehouse warehouse = warehouseService.requireActive(original.warehouse.id);

        return applyAndTrace(
                variant,
                warehouse,
                InventoryMovementType.COMPENSATION,
                original.quantity.negate(),
                original.reservedDelta.negate(),
                sourceDocument.trim(),
                reason.trim(),
                original
        );
    }

    private MovementApplicationResult applyAndTrace(
            ProductVariant variant,
            Warehouse warehouse,
            InventoryMovementType type,
            BigDecimal availableDelta,
            BigDecimal reservedDelta,
            String sourceDocument,
            String reason,
            InventoryMovement compensatesMovement
    ) {
        InventoryBalance balance = balanceRepository.findForUpdate(variant.id, warehouse.id)
                .orElseGet(() -> createBalance(variant, warehouse));

        BigDecimal previousAvailable = valueOrZero(balance.availableQuantity);
        BigDecimal previousReserved = valueOrZero(balance.reservedQuantity);
        BigDecimal newAvailable = previousAvailable.add(availableDelta);
        BigDecimal newReserved = previousReserved.add(reservedDelta);

        if (newAvailable.signum() < 0) {
            throw new BusinessConflictException("El movimiento dejaría el inventario disponible en negativo");
        }
        if (newReserved.signum() < 0) {
            throw new BusinessConflictException("El movimiento dejaría el inventario reservado en negativo");
        }

        balance.availableQuantity = newAvailable;
        balance.reservedQuantity = newReserved;
        InventoryBalance savedBalance = balanceRepository.save(balance);

        InventoryMovement movement = new InventoryMovement();
        movement.variant = variant;
        movement.warehouse = warehouse;
        movement.type = type;
        movement.quantity = availableDelta;
        movement.reservedDelta = reservedDelta;
        movement.previousAvailable = previousAvailable;
        movement.newAvailable = newAvailable;
        movement.previousReserved = previousReserved;
        movement.newReserved = newReserved;
        movement.sourceDocument = sourceDocument;
        movement.performedBy = currentUsername();
        movement.movementAt = Instant.now();
        movement.reason = reason;
        movement.compensatesMovement = compensatesMovement;

        InventoryMovement savedMovement = movementRepository.save(movement);
        return new MovementApplicationResult(savedBalance, savedMovement);
    }

    private void validateMovementNature(
            InventoryMovementType type,
            BigDecimal availableDelta,
            BigDecimal reservedDelta
    ) {
        if (type == null) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio");
        }
        if (availableDelta.signum() == 0 && reservedDelta.signum() == 0) {
            throw new IllegalArgumentException("El movimiento debe modificar al menos un saldo");
        }
        if (type == InventoryMovementType.SALE && availableDelta.signum() >= 0) {
            throw new IllegalArgumentException("Una venta debe disminuir el saldo disponible");
        }
        if (type == InventoryMovementType.RECEIPT && availableDelta.signum() <= 0) {
            throw new IllegalArgumentException("Una recepción debe aumentar el saldo disponible");
        }
    }

    private void validateDocument(String sourceDocument) {
        if (sourceDocument == null || sourceDocument.isBlank()) {
            throw new IllegalArgumentException("El documento origen es obligatorio");
        }
        if (sourceDocument.trim().length() > 255) {
            throw new IllegalArgumentException("El documento origen no puede superar 255 caracteres");
        }
    }

    private InventoryBalance createBalance(ProductVariant variant, Warehouse warehouse) {
        InventoryBalance balance = new InventoryBalance();
        balance.variant = variant;
        balance.warehouse = warehouse;
        balance.availableQuantity = BigDecimal.ZERO;
        balance.reservedQuantity = BigDecimal.ZERO;
        balance.minimumQuantity = BigDecimal.ZERO;
        return balance;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "sistema";
        }
        return truncate(authentication.getName().trim(), 255);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record MovementApplicationResult(
            InventoryBalance balance,
            InventoryMovement movement
    ) {
    }
}
