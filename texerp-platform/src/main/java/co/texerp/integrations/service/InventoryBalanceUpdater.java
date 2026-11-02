
package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.InventoryBalance;
import co.texerp.integrations.domain.InventoryMovement;
import co.texerp.integrations.domain.InventoryMovementType;
import co.texerp.integrations.domain.ProductVariant;
import co.texerp.integrations.domain.Warehouse;
import co.texerp.integrations.repository.InventoryBalanceRepository;
import co.texerp.integrations.repository.InventoryMovementRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnitUtil;

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

    @PersistenceContext
    private EntityManager entityManager;

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
     * dentro de la misma transacción de negocio.
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
        validateIds(variantId, warehouseId);
        validateDocument(sourceDocument);

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
                    "Los ajustes manuales deben registrarse mediante " +
                            "el servicio de ajustes"
            );
        }

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
        validateReason(reason, "ajuste");

        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException(
                    "La cantidad del ajuste debe ser diferente de cero"
            );
        }

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
     * Revierte el impacto de un movimiento mediante uno nuevo.
     * El movimiento original nunca se modifica ni se elimina.
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

        if (original.type == InventoryMovementType.COMPENSATION) {
            throw new BusinessConflictException(
                    "No se permite compensar directamente " +
                            "un movimiento de compensación"
            );
        }

        if (original.variant == null) {
            throw new BusinessConflictException(
                    "El movimiento " + original.id +
                            " no tiene una variante asociada"
            );
        }

        if (original.warehouse == null) {
            throw new BusinessConflictException(
                    "El movimiento " + original.id +
                            " no tiene una bodega asociada"
            );
        }

        // Recuperar identificadores sin acceder a campos de proxies.
        Long variantId = getEntityId(
                original.variant,
                "variante del movimiento " + original.id
        );

        Long warehouseId = getEntityId(
                original.warehouse,
                "bodega del movimiento " + original.id
        );

        validateIds(variantId, warehouseId);

        if (original.quantity == null) {
            throw new BusinessConflictException(
                    "El movimiento " + original.id +
                            " no tiene registrada la variación disponible"
            );
        }

        if (movementRepository.existsCompensationFor(original.id)) {
            throw new BusinessConflictException(
                    "El movimiento " + original.id +
                            " ya tiene una compensación registrada"
            );
        }

        /*
         * Las compensaciones utilizan las entidades históricas.
         * No se exige que sigan activas porque pudieron ser
         * desactivadas después de registrar el movimiento.
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
     * Actualiza el balance y registra el movimiento de inventario.
     * Todo ocurre dentro de la transacción de negocio.
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
        if (variant == null) {
            throw new IllegalArgumentException(
                    "La variante de producto es obligatoria"
            );
        }

        if (warehouse == null) {
            throw new IllegalArgumentException(
                    "La bodega es obligatoria"
            );
        }

        // Obtener los identificadores reales mediante JPA.
        Long variantId = getEntityId(
                variant,
                "variante de producto"
        );

        Long warehouseId = getEntityId(
                warehouse,
                "bodega"
        );

        validateIds(variantId, warehouseId);

        BigDecimal safeAvailableDelta =
                valueOrZero(availableDelta);

        BigDecimal safeReservedDelta =
                valueOrZero(reservedDelta);

        InventoryBalance balance = balanceRepository
                .findForUpdate(variantId, warehouseId)
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

        // Controlar inventario disponible negativo.
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

        // Controlar inventario reservado negativo.
        if (newReserved.signum() < 0) {
            throw new BusinessConflictException(
                    "El movimiento dejaría el inventario " +
                            "reservado en negativo"
            );
        }

        // Actualizar balance.
        balance.availableQuantity = newAvailable;
        balance.reservedQuantity = newReserved;

        InventoryBalance savedBalance =
                balanceRepository.save(balance);

        // Crear trazabilidad del movimiento.
        InventoryMovement movement = new InventoryMovement();

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
     * Obtiene el identificador JPA sin leer directamente
     * campos de entidades que pueden ser proxies LAZY.
     */
    private Long getEntityId(
            Object entity,
            String entityName
    ) {
        if (entity == null) {
            throw new BusinessConflictException(
                    "La entidad " + entityName +
                            " no está asociada"
            );
        }

        PersistenceUnitUtil persistenceUtil = entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil();

        Object identifier = persistenceUtil.getIdentifier(entity);

        if (!(identifier instanceof Long id)) {
            throw new BusinessConflictException(
                    "No fue posible obtener el identificador " +
                            "de " + entityName
            );
        }

        return id;
    }

    /**
     * Valida los identificadores requeridos.
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

    /**
     * Valida la naturaleza del movimiento.
     */
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

    /**
     * Valida documento origen.
     */
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

    /**
     * Valida motivo del ajuste o compensación.
     */
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

    /**
     * Crea un balance inicial cuando todavía no existe.
     */
    private InventoryBalance createBalance(
            ProductVariant variant,
            Warehouse warehouse
    ) {
        InventoryBalance balance = new InventoryBalance();

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

    /**
     * Recupera el usuario autenticado.
     */
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
