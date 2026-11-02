
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
     * Aplica un movimiento confirmado y registra su trazabilidad
     * dentro de la transacción de negocio.
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
        if (type == null) {
            throw new IllegalArgumentException(
                    "El tipo de movimiento es obligatorio"
            );
        }

        if (type == InventoryMovementType.COMPENSATION) {
            throw new IllegalArgumentException(
                    "Los movimientos compensatorios deben generarse " +
                            "a partir de un movimiento existente"
            );
        }

        if (type == InventoryMovementType.ADJUSTMENT) {
            throw new IllegalArgumentException(
                    "Los ajustes manuales deben registrarse " +
                            "mediante el servicio de ajustes"
            );
        }

        validateIds(variantId, warehouseId);
        validateDocument(sourceDocument);

        BigDecimal safeAvailableDelta = valueOrZero(availableDelta);
        BigDecimal safeReservedDelta = valueOrZero(reservedDelta);

        validateMovementNature(
                type,
                safeAvailableDelta,
                safeReservedDelta
        );

        ProductVariant variant =
                variantService.requireActive(variantId);

        Warehouse warehouse =
                warehouseService.requireActive(warehouseId);

        return applyAndTrace(
                variant,
                warehouse,
                type,
                safeAvailableDelta,
                safeReservedDelta,
                sourceDocument.trim(),
                null,
                null,
                false
        );
    }

    /**
     * Aplica un ajuste manual de inventario.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovementApplicationResult applyInventoryAdjustment(
            Long variantId,
            Long warehouseId,
            BigDecimal quantity,
            String sourceDocument,
            String reason,
            boolean allowNegativeAvailable
    ) {
        validateIds(variantId, warehouseId);
        validateDocument(sourceDocument);

        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException(
                    "La cantidad del ajuste debe ser diferente de cero"
            );
        }

        validateReason(reason, "ajuste");

        ProductVariant variant =
                variantService.requireActive(variantId);

        Warehouse warehouse =
                warehouseService.requireActive(warehouseId);

        return applyAndTrace(
                variant,
                warehouse,
                InventoryMovementType.ADJUSTMENT,
                quantity,
                BigDecimal.ZERO,
                sourceDocument.trim(),
                reason.trim(),
                null,
                allowNegativeAvailable
        );
    }

    /**
     * Revierte el impacto de un movimiento confirmado.
     *
     * El movimiento original permanece intacto.
     * Se genera un nuevo movimiento de tipo COMPENSATION.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovementApplicationResult applyCompensatingMovement(
            InventoryMovement original,
            String sourceDocument,
            String reason
    ) {
        if (original == null || original.id == null) {
            throw new IllegalArgumentException(
                    "El movimiento original es obligatorio"
            );
        }

        validateDocument(sourceDocument);
        validateReason(reason, "compensación");

        // Validar las referencias antes de consultar repositorios.
        if (original.variant == null ||
                original.variant.id == null) {
            throw new BusinessConflictException(
                    "No se puede compensar el movimiento " +
                            original.id +
                            ": no tiene una variante de producto válida"
            );
        }

        if (original.warehouse == null ||
                original.warehouse.id == null) {
            throw new BusinessConflictException(
                    "No se puede compensar el movimiento " +
                            original.id +
                            ": no tiene una bodega válida"
            );
        }

        if (original.quantity == null) {
            throw new BusinessConflictException(
                    "No se puede compensar el movimiento " +
                            original.id +
                            ": no tiene registrada la variación disponible"
            );
        }

        if (original.type == InventoryMovementType.COMPENSATION) {
            throw new BusinessConflictException(
                    "No se permite compensar directamente " +
                            "un movimiento de compensación"
            );
        }

        // Verificar que no exista ya una compensación.
        // Evita devolver inventario dos veces.
        if (movementRepository.existsCompensationFor(original.id)) {
            throw new BusinessConflictException(
                    "El movimiento " + original.id +
                            " ya tiene una compensación registrada"
            );
        }

        /*
         * Se reutilizan las entidades relacionadas con el
         * movimiento histórico.
         *
         * No se utiliza requireActive(), porque una variante
         * o bodega podría haberse desactivado después.
         *
         * Las entidades deben seguir existiendo en la BD.
         */
        ProductVariant variant = original.variant;
        Warehouse warehouse = original.warehouse;

        BigDecimal availableDelta =
                original.quantity.negate();

        BigDecimal reservedDelta =
                valueOrZero(original.reservedDelta).negate();

        return applyAndTrace(
                variant,
                warehouse,
                InventoryMovementType.COMPENSATION,
                availableDelta,
                reservedDelta,
                sourceDocument.trim(),
                reason.trim(),
                original,
                false
        );
    }

    /**
     * Actualiza el balance y registra el movimiento
     * dentro de la misma transacción.
     */
    private MovementApplicationResult applyAndTrace(
            ProductVariant variant,
            Warehouse warehouse,
            InventoryMovementType type,
            BigDecimal availableDelta,
            BigDecimal reservedDelta,
            String sourceDocument,
            String reason,
            InventoryMovement compensatesMovement,
            boolean allowNegativeAvailable
    ) {
        if (variant == null || variant.id == null) {
            throw new IllegalArgumentException(
                    "La variante de producto es obligatoria"
            );
        }

        if (warehouse == null || warehouse.id == null) {
            throw new IllegalArgumentException(
                    "La bodega es obligatoria"
            );
        }

        BigDecimal safeAvailableDelta =
                valueOrZero(availableDelta);

        BigDecimal safeReservedDelta =
                valueOrZero(reservedDelta);

        InventoryBalance balance = balanceRepository
                .findForUpdate(variant.id, warehouse.id)
                .orElseGet(() ->
                        createBalance(variant, warehouse)
                );

        BigDecimal previousAvailable =
                valueOrZero(balance.availableQuantity);

        BigDecimal previousReserved =
                valueOrZero(balance.reservedQuantity);

        BigDecimal newAvailable =
                previousAvailable.add(safeAvailableDelta);

        BigDecimal newReserved =
                previousReserved.add(safeReservedDelta);

        if (newAvailable.signum() < 0 &&
                !allowNegativeAvailable) {
            String operation =
                    type == InventoryMovementType.ADJUSTMENT
                            ? "El ajuste"
                            : "El movimiento";

            throw new BusinessConflictException(
                    operation +
                            " dejaría el inventario disponible en negativo. " +
                            "Saldo disponible: " +
                            previousAvailable.toPlainString() +
                            ", variación solicitada: " +
                            safeAvailableDelta.toPlainString()
            );
        }

        if (newReserved.signum() < 0) {
            throw new BusinessConflictException(
                    "El movimiento dejaría el inventario " +
                            "reservado en negativo"
            );
        }

        balance.availableQuantity = newAvailable;
        balance.reservedQuantity = newReserved;

        InventoryBalance savedBalance =
                balanceRepository.save(balance);

        InventoryMovement movement =
                new InventoryMovement();

        movement.variant = variant;
        movement.warehouse = warehouse;
        movement.type = type;
        movement.quantity = safeAvailableDelta;
        movement.reservedDelta = safeReservedDelta;
        movement.previousAvailable = previousAvailable;
        movement.newAvailable = newAvailable;
        movement.previousReserved = previousReserved;
        movement.newReserved = newReserved;
        movement.sourceDocument = sourceDocument;
        movement.performedBy = currentUsername();
        movement.movementAt = Instant.now();
        movement.reason = reason;
        movement.compensatesMovement = compensatesMovement;

        InventoryMovement savedMovement =
                movementRepository.save(movement);

        return new MovementApplicationResult(
                savedBalance,
                savedMovement
        );
    }

    /**
     * Valida los identificadores antes de consultar la BD.
     */
    private void validateIds(
            Long variantId,
            Long warehouseId
    ) {
        if (variantId == null) {
            throw new IllegalArgumentException(
                    "El ID de la variante de producto es obligatorio"
            );
        }

        if (warehouseId == null) {
            throw new IllegalArgumentException(
                    "El ID de la bodega es obligatorio"
            );
        }
    }

    private void validateMovementNature(
            InventoryMovementType type,
            BigDecimal availableDelta,
            BigDecimal reservedDelta
    ) {
        if (type == null) {
            throw new IllegalArgumentException(
                    "El tipo de movimiento es obligatorio"
            );
        }

        if (availableDelta.signum() == 0 &&
                reservedDelta.signum() == 0) {
            throw new IllegalArgumentException(
                    "El movimiento debe modificar al menos un saldo"
            );
        }

        if (type == InventoryMovementType.SALE &&
                availableDelta.signum() >= 0) {
            throw new IllegalArgumentException(
                    "Una venta debe disminuir el saldo disponible"
            );
        }

        if (type == InventoryMovementType.RECEIPT &&
                availableDelta.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Una recepción debe aumentar el saldo disponible"
            );
        }
    }

    private void validateDocument(String sourceDocument) {
        if (sourceDocument == null ||
                sourceDocument.isBlank()) {
            throw new IllegalArgumentException(
                    "El documento origen es obligatorio"
            );
        }

        if (sourceDocument.trim().length() > 255) {
            throw new IllegalArgumentException(
                    "El documento origen no puede superar 255 caracteres"
            );
        }
    }

    private void validateReason(
            String reason,
            String operation
    ) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "El motivo de la " +
                            operation + " es obligatorio"
            );
        }

        if (reason.trim().length() > 500) {
            throw new IllegalArgumentException(
                    "El motivo de la " +
                            operation +
                            " no puede superar 500 caracteres"
            );
        }
    }

    private InventoryBalance createBalance(
            ProductVariant variant,
            Warehouse warehouse
    ) {
        InventoryBalance balance =
                new InventoryBalance();

        balance.variant = variant;
        balance.warehouse = warehouse;
        balance.availableQuantity = BigDecimal.ZERO;
        balance.reservedQuantity = BigDecimal.ZERO;
        balance.minimumQuantity = BigDecimal.ZERO;

        return balance;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null ||
                authentication.getName() == null ||
                authentication.getName().isBlank()) {
            return "sistema";
        }

        return truncate(
                authentication.getName().trim(),
                255
        );
    }

    private String truncate(
            String value,
            int maxLength
    ) {
        if (value == null ||
                value.length() <= maxLength) {
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
