
package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.*;
import co.texerp.integrations.dto.PurchaseOrderDtos.*;
import co.texerp.integrations.repository.*;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final ProductVariantRepository variants;
    private final AuditService audit;
    private final PersistenceUnitUtil persistenceUnitUtil;

    public PurchaseOrderService(
            PurchaseOrderRepository orders,
            SupplierRepository suppliers,
            ProductVariantRepository variants,
            AuditService audit,
            EntityManagerFactory entityManagerFactory
    ) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.variants = variants;
        this.audit = audit;
        this.persistenceUnitUtil =
                entityManagerFactory.getPersistenceUnitUtil();
    }

    /**
     * Crear una nueva orden de compra.
     */
    @Transactional
    public Response create(CreateRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "La solicitud es obligatoria"
            );
        }

        if (request.supplierId() == null) {
            throw new IllegalArgumentException(
                    "El proveedor es obligatorio"
            );
        }

        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException(
                    "La orden debe contener al menos una línea"
            );
        }

        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Proveedor no encontrado"
                        )
                );

        if (!supplier.active) {
            throw new IllegalArgumentException(
                    "El proveedor está inactivo"
            );
        }

        PurchaseOrder order = new PurchaseOrder();

        order.supplier = supplier;
        order.orderedAt = Instant.now();
        order.status = PurchaseOrderStatus.PENDING_APPROVAL;
        order.total = BigDecimal.ZERO;

        for (int i = 0; i < request.lines().size(); i++) {

            LineRequest input = request.lines().get(i);
            int lineNumber = i + 1;

            if (input == null) {
                throw new IllegalArgumentException(
                        "Línea " + lineNumber
                                + ": información obligatoria"
                );
            }

            if (input.variantId() == null) {
                throw new IllegalArgumentException(
                        "Línea " + lineNumber
                                + ": variante obligatoria"
                );
            }

            if (input.quantity() == null
                    || input.quantity().signum() <= 0
                    || input.unitCost() == null
                    || input.unitCost().signum() <= 0) {

                throw new IllegalArgumentException(
                        "Línea " + lineNumber
                                + ": cantidad y costo deben ser mayores que cero"
                );
            }

            if (input.quantity().scale() > 3
                    || input.unitCost().scale() > 2) {

                throw new IllegalArgumentException(
                        "Línea " + lineNumber
                                + ": máximo 3 decimales en cantidad "
                                + "y 2 en costo"
                );
            }

            ProductVariant variant = variants.findById(input.variantId())
                    .orElseThrow(() ->
                            new EntityNotFoundException(
                                    "Variante no encontrada en línea "
                                            + lineNumber
                            )
                    );

            if (!variant.active || !variant.product.active) {
                throw new IllegalArgumentException(
                        "Línea " + lineNumber
                                + ": variante o producto inactivo"
                );
            }

            PurchaseOrderLine detail = new PurchaseOrderLine();

            detail.purchaseOrder = order;
            detail.variant = variant;
            detail.quantity = input.quantity();
            detail.receivedQuantity = BigDecimal.ZERO;
            detail.unitCost = input.unitCost();

            detail.total = input.quantity()
                    .multiply(input.unitCost())
                    .setScale(2, RoundingMode.HALF_UP);

            order.lines.add(detail);
            order.total = order.total.add(detail.total);
        }

        orders.saveAndFlush(order);

        order.number = "OC-" + String.format("%08d", order.id);

        orders.saveAndFlush(order);

        audit.log(
                "PURCHASE_ORDER_CREATE",
                "PurchaseOrder",
                order.id,
                "Orden de compra creada"
                        + ", estado=" + order.status
                        + ", usuario=" + username()
                        + ", fecha=" + order.orderedAt,
                null
        );

        return map(order);
    }

    /**
     * Consultar una orden de compra por ID.
     */
    @Transactional(readOnly = true)
    public Response findById(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "El identificador de la orden es obligatorio"
            );
        }

        PurchaseOrder order = orders.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Orden de compra no encontrada"
                        )
                );

        return map(order);
    }

    /**
     * Buscar órdenes de compra con filtros dinámicos.
     *
     * Evita condiciones del tipo ":param IS NULL",
     * que pueden provocar SQLState 42P18 en PostgreSQL.
     */
    @Transactional(readOnly = true)
    public Page<Response> search(
            Long supplierId,
            PurchaseOrderStatus status,
            Instant from,
            Instant to,
            Long variantId,
            int page,
            int size
    ) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "La fecha inicial no puede ser mayor "
                            + "que la fecha final"
            );
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Paginación inválida: page debe ser >= 0 "
                            + "y size debe estar entre 1 y 100"
            );
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("orderedAt")
                        .descending()
                        .and(Sort.by("id").descending())
        );

        Specification<PurchaseOrder> specification =
                (root, query, cb) -> {

                    List<Predicate> predicates = new ArrayList<>();

                    // Filtrar por proveedor.
                    if (supplierId != null) {
                        predicates.add(
                                cb.equal(
                                        root.get("supplier").get("id"),
                                        supplierId
                                )
                        );
                    }

                    // Filtrar por estado.
                    if (status != null) {
                        predicates.add(
                                cb.equal(
                                        root.get("status"),
                                        status
                                )
                        );
                    }

                    // Fecha inicial inclusiva.
                    if (from != null) {
                        predicates.add(
                                cb.greaterThanOrEqualTo(
                                        root.<Instant>get("orderedAt"),
                                        from
                                )
                        );
                    }

                    // Fecha final inclusiva.
                    if (to != null) {
                        predicates.add(
                                cb.lessThanOrEqualTo(
                                        root.<Instant>get("orderedAt"),
                                        to
                                )
                        );
                    }

                    // Filtrar por variante sin duplicar órdenes.
                    if (variantId != null) {

                        var subquery = query.subquery(Long.class);

                        var line = subquery.from(
                                PurchaseOrderLine.class
                        );

                        subquery.select(
                                line.<Long>get("id")
                        );

                        subquery.where(
                                cb.equal(
                                        line.get("purchaseOrder").get("id"),
                                        root.get("id")
                                ),
                                cb.equal(
                                        line.get("variant").get("id"),
                                        variantId
                                )
                        );

                        predicates.add(
                                cb.exists(subquery)
                        );
                    }

                    return cb.and(
                            predicates.toArray(new Predicate[0])
                    );
                };

        return orders.findAll(specification, pageable)
                .map(this::map);
    }

    /**
     * Aprobar una orden pendiente.
     */
    @Transactional
    public Response approve(Long id) {
        return decide(id, null);
    }

    /**
     * Rechazar una orden pendiente.
     */
    @Transactional
    public Response reject(Long id, String reason) {

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "El motivo de rechazo es obligatorio"
            );
        }

        String rejectionReason = reason.trim();

        if (rejectionReason.length() > 500) {
            throw new IllegalArgumentException(
                    "El motivo supera 500 caracteres"
            );
        }

        return decide(id, rejectionReason);
    }

    /**
     * Aprobar o rechazar una orden.
     *
     * Conserva el bloqueo pesimista para evitar
     * decisiones concurrentes.
     */
    private Response decide(
            Long id,
            String rejectionReason
    ) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "El identificador de la orden es obligatorio"
            );
        }

        PurchaseOrder order = orders.findForUpdate(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Orden de compra no encontrada"
                        )
                );

        if (order.status != PurchaseOrderStatus.PENDING_APPROVAL) {
            throw new BusinessConflictException(
                    "La orden ya fue decidida: " + order.status
            );
        }

        PurchaseOrderStatus previous = order.status;

        order.status = rejectionReason == null
                ? PurchaseOrderStatus.APPROVED
                : PurchaseOrderStatus.REJECTED;

        order.decidedAt = Instant.now();
        order.decidedBy = username();
        order.rejectionReason = rejectionReason;

        orders.saveAndFlush(order);

        audit.log(
                "PURCHASE_ORDER_DECISION",
                "PurchaseOrder",
                order.id,
                "Cambio de estado de la orden de compra"
                        + ", usuario=" + order.decidedBy
                        + ", fecha=" + order.decidedAt
                        + (rejectionReason == null
                        ? ""
                        : ", motivo=" + rejectionReason),
                null,
                previous.name(),
                order.status.name()
        );

        return map(order);
    }

    /**
     * Convertir PurchaseOrder a Response.
     *
     * Obtiene los identificadores de las relaciones
     * LAZY mediante PersistenceUnitUtil.
     */
    private Response map(PurchaseOrder order) {

        Long supplierId = getEntityId(order.supplier);

        return new Response(
                order.id,
                order.number,
                supplierId,
                order.orderedAt,
                order.status,
                order.total,
                order.decidedAt,
                order.decidedBy,
                order.rejectionReason,

                order.lines.stream()
                        .map(line -> {

                            Long variantId = getEntityId(line.variant);

                            return new LineResponse(
                                    line.id,
                                    variantId,
                                    line.quantity,
                                    line.receivedQuantity,
                                    line.unitCost,
                                    line.total
                            );
                        })
                        .toList()
        );
    }

    /**
     * Obtener el ID de una entidad JPA,
     * incluso cuando sea un proxy de Hibernate.
     */
    private Long getEntityId(Object entity) {

        if (entity == null) {
            return null;
        }

        Object identifier =
                persistenceUnitUtil.getIdentifier(entity);

        if (identifier == null) {
            return null;
        }

        if (!(identifier instanceof Long)) {
            throw new IllegalStateException(
                    "Se esperaba un identificador Long, "
                            + "pero se recibió: "
                            + identifier.getClass().getName()
            );
        }

        return (Long) identifier;
    }

    /**
     * Obtener el usuario autenticado.
     */
    private String username() {

        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        return authentication == null
                || authentication.getName() == null
                ? "sistema"
                : authentication.getName();
    }
}
