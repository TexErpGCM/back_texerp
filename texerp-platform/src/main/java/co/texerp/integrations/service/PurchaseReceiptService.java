
package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.*;
import co.texerp.integrations.dto.PurchaseReceiptDtos.*;
import co.texerp.integrations.repository.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PurchaseReceiptService {

    private final PurchaseOrderRepository orders;
    private final PurchaseReceiptRepository receipts;
    private final WarehouseService warehouses;
    private final InventoryBalanceUpdater inventory;
    private final AuditService audit;

    @PersistenceContext
    private EntityManager entityManager;

    public PurchaseReceiptService(
            PurchaseOrderRepository orders,
            PurchaseReceiptRepository receipts,
            WarehouseService warehouses,
            InventoryBalanceUpdater inventory,
            AuditService audit
    ) {
        this.orders = orders;
        this.receipts = receipts;
        this.warehouses = warehouses;
        this.inventory = inventory;
        this.audit = audit;
    }

    /**
     * Registra una recepción parcial o completa de una orden.
     *
     * Todas las operaciones se ejecutan dentro de una
     * única transacción.
     */
    @Transactional
    public Response receive(Long orderId, CreateRequest request) {

        if (orderId == null) {
            throw new IllegalArgumentException(
                    "El ID de la orden es obligatorio"
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Los datos de recepción son obligatorios"
            );
        }

        if (request.warehouseId() == null) {
            throw new IllegalArgumentException(
                    "La bodega es obligatoria"
            );
        }

        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException(
                    "Debes seleccionar al menos una línea para recibir"
            );
        }

        if (request.document() == null ||
                request.document().isBlank()) {
            throw new IllegalArgumentException(
                    "El documento de recepción es obligatorio"
            );
        }

        String document = request.document().trim();

        if (document.length() > 255) {
            throw new IllegalArgumentException(
                    "El documento supera 255 caracteres"
            );
        }

        // Bloquear la orden durante la recepción.
        PurchaseOrder order = orders.findForUpdate(orderId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Orden no encontrada"
                        )
                );

        if (order.status != PurchaseOrderStatus.APPROVED
                && order.status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessConflictException(
                    "La orden no admite recepciones en estado "
                            + order.status
            );
        }

        Warehouse warehouse =
                warehouses.requireActive(request.warehouseId());

        Long warehouseId = getEntityId(
                warehouse,
                "bodega"
        );

        HashSet<Long> lineIds = new HashSet<>();

        /*
         * Validar todas las líneas antes de crear
         * movimientos de inventario.
         */
        for (int i = 0; i < request.lines().size(); i++) {

            LineRequest input = request.lines().get(i);
            int position = i + 1;

            if (input == null) {
                throw new IllegalArgumentException(
                        "Línea " + position +
                                ": los datos son obligatorios"
                );
            }

            PurchaseOrderLine line = findLine(
                    order,
                    input.orderLineId(),
                    position
            );

            if (!lineIds.add(line.id)) {
                throw new IllegalArgumentException(
                        "Línea " + position +
                                ": está duplicada"
                );
            }

            // Validar la asociación mediante JPA.
            validateVariant(line, position);

            if (line.quantity == null ||
                    line.receivedQuantity == null) {
                throw new BusinessConflictException(
                        "La línea " + line.id +
                                " presenta cantidades inconsistentes"
                );
            }

            if (input.quantity() == null
                    || input.quantity().signum() <= 0
                    || input.quantity().scale() > 3) {
                throw new IllegalArgumentException(
                        "Línea " + position +
                                ": cantidad inválida"
                );
            }

            BigDecimal pending = line.quantity.subtract(
                    line.receivedQuantity
            );

            if (pending.signum() <= 0) {
                throw new BusinessConflictException(
                        "La línea " + line.id +
                                " no tiene cantidades pendientes"
                );
            }

            if (input.quantity().compareTo(pending) > 0) {
                throw new BusinessConflictException(
                        "Línea " + position +
                                " (" + line.id + ")" +
                                ": la cantidad supera el pendiente de " +
                                pending.toPlainString()
                );
            }

            if (line.unitCost == null) {
                throw new BusinessConflictException(
                        "La línea " + line.id +
                                " no tiene costo unitario"
                );
            }
        }

        /*
         * Crear el encabezado.
         */
        PurchaseReceipt receipt = new PurchaseReceipt();

        receipt.purchaseOrder = order;
        receipt.warehouse = warehouse;
        receipt.document = document;
        receipt.receivedAt = Instant.now();
        receipt.receivedBy = currentUsername();

        // Número temporal para persistir y obtener el ID.
        receipt.number = "TMP-" +
                UUID.randomUUID().toString().substring(0, 24);

        receipts.saveAndFlush(receipt);

        receipt.number = "REC-" +
                String.format("%08d", receipt.id);

        /*
         * Registrar entradas de inventario.
         */
        for (LineRequest input : request.lines()) {

            PurchaseOrderLine line = findLine(
                    order,
                    input.orderLineId(),
                    0
            );

            // Obtener ID real de la variante con JPA.
            Long variantId = getVariantId(line.variant);

            var movement = inventory.applyConfirmedMovement(
                    variantId,
                    warehouseId,
                    InventoryMovementType.RECEIPT,
                    input.quantity(),
                    BigDecimal.ZERO,
                    receipt.number
            );

            PurchaseReceiptLine detail = new PurchaseReceiptLine();

            detail.receipt = receipt;
            detail.orderLine = line;
            detail.quantity = input.quantity();
            detail.unitCost = line.unitCost;

            detail.total = input.quantity()
                    .multiply(line.unitCost)
                    .setScale(2, RoundingMode.HALF_UP);

            detail.movement = movement.movement();

            receipt.lines.add(detail);

            // Actualizar cantidad recibida.
            line.receivedQuantity = line.receivedQuantity.add(
                    input.quantity()
            );

            audit.log(
                    "INVENTORY_RECEIPT",
                    "InventoryMovement",
                    movement.movement().id,
                    "Orden=" + order.id
                            + ", recepción=" + receipt.id
                            + ", variante=" + variantId
                            + ", antes="
                            + movement.movement().previousAvailable
                            + ", después="
                            + movement.movement().newAvailable,
                    null
            );
        }

        /*
         * Actualizar estado de la orden.
         */
        PurchaseOrderStatus previous = order.status;

        boolean fullyReceived = order.lines.stream()
                .allMatch(line ->
                        line.quantity != null
                                && line.receivedQuantity != null
                                && line.receivedQuantity.compareTo(
                                line.quantity
                        ) >= 0
                );

        order.status = fullyReceived
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;

        receipts.saveAndFlush(receipt);
        orders.saveAndFlush(order);

        audit.log(
                "PURCHASE_RECEIPT",
                "PurchaseReceipt",
                receipt.id,
                "Orden=" + order.id
                        + ", estado anterior=" + previous
                        + ", nuevo=" + order.status
                        + ", bodega=" + warehouseId
                        + ", documento=" + receipt.document,
                null
        );

        return map(receipt);
    }

    /**
     * Consulta una recepción por ID.
     */
    @Transactional(readOnly = true)
    public Response findById(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "El ID de la recepción es obligatorio"
            );
        }

        PurchaseReceipt receipt = receipts.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Recepción no encontrada"
                        )
                );

        return map(receipt);
    }

    /**
     * Consulta recepciones asociadas a una orden.
     */
    @Transactional(readOnly = true)
    public List<Response> byOrder(Long orderId) {

        if (orderId == null) {
            throw new IllegalArgumentException(
                    "El ID de la orden es obligatorio"
            );
        }

        if (!orders.existsById(orderId)) {
            throw new EntityNotFoundException(
                    "Orden no encontrada"
            );
        }

        return receipts
                .findByPurchaseOrderIdOrderByReceivedAtDesc(orderId)
                .stream()
                .map(this::map)
                .toList();
    }

    /**
     * Busca una línea perteneciente a la orden.
     */
    private PurchaseOrderLine findLine(
            PurchaseOrder order,
            Long id,
            int position
    ) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Línea " + position +
                            ": el ID de la línea es obligatorio"
            );
        }

        return order.lines.stream()
                .filter(line -> Objects.equals(line.id, id))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Línea " + position +
                                        ": no pertenece a la orden"
                        )
                );
    }

    /**
     * Valida la variante sin acceder directamente
     * al identificador de un proxy Hibernate.
     */
    private void validateVariant(
            PurchaseOrderLine line,
            int position
    ) {

        if (line.variant == null) {
            throw new BusinessConflictException(
                    "La línea " + position +
                            " de la orden no tiene una variante asociada"
            );
        }

        Long variantId = getVariantId(line.variant);

        if (variantId == null) {
            throw new BusinessConflictException(
                    "La línea " + position +
                            " no tiene un identificador válido de variante"
            );
        }
    }

    /**
     * Obtiene el identificador de una variante
     * usando la API de persistencia JPA.
     */
    private Long getVariantId(ProductVariant variant) {

        if (variant == null) {
            throw new BusinessConflictException(
                    "La variante de producto no está asociada"
            );
        }

        return getEntityId(variant, "variante de producto");
    }

    /**
     * Obtiene el identificador JPA de una entidad.
     * Es compatible con entidades gestionadas y
     * referencias LAZY de Hibernate.
     */
    private Long getEntityId(Object entity, String entityName) {

        if (entity == null) {
            throw new BusinessConflictException(
                    "La entidad " + entityName +
                            " no está asociada"
            );
        }

        Object identifier = entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .getIdentifier(entity);

        if (!(identifier instanceof Long id)) {
            throw new BusinessConflictException(
                    "No fue posible obtener el identificador " +
                            "de " + entityName
            );
        }

        return id;
    }

    /**
     * Recupera el usuario autenticado.
     */
    private String currentUsername() {

        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            return "sistema";
        }

        return authentication.getName();
    }

    /**
     * Convierte una recepción a DTO.
     */
    private Response map(PurchaseReceipt receipt) {

        return new Response(
                receipt.id,
                receipt.number,
                receipt.receivedAt,
                getEntityId(receipt.purchaseOrder, "orden de compra"),
                getEntityId(receipt.warehouse, "bodega"),
                receipt.document,
                receipt.receivedBy,
                receipt.lines.stream()
                        .map(line -> new LineResponse(
                                line.id,
                                getEntityId(
                                        line.orderLine,
                                        "línea de orden"
                                ),
                                getVariantId(line.orderLine.variant),
                                line.quantity,
                                line.unitCost,
                                line.total,
                                getEntityId(
                                        line.movement,
                                        "movimiento de inventario"
                                )
                        ))
                        .toList()
        );
    }
}
