package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.*;
import co.texerp.integrations.dto.PurchaseOrderDtos.*;
import co.texerp.integrations.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class PurchaseOrderService {
    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final ProductVariantRepository variants;
    private final AuditService audit;

    public PurchaseOrderService(PurchaseOrderRepository orders, SupplierRepository suppliers,
                                ProductVariantRepository variants, AuditService audit) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.variants = variants;
        this.audit = audit;
    }

    @Transactional
    public Response create(CreateRequest request) {
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new EntityNotFoundException("Proveedor no encontrado"));
        if (!supplier.active) throw new IllegalArgumentException("El proveedor está inactivo");
        PurchaseOrder order = new PurchaseOrder();
        order.supplier = supplier;
        order.orderedAt = Instant.now();
        for (int i = 0; i < request.lines().size(); i++) {
            LineRequest input = request.lines().get(i);
            int line = i + 1;
            if (input.quantity() == null || input.quantity().signum() <= 0
                    || input.unitCost() == null || input.unitCost().signum() <= 0) {
                throw new IllegalArgumentException("Línea " + line + ": cantidad y costo deben ser mayores que cero");
            }
            if (input.quantity().scale() > 3 || input.unitCost().scale() > 2) {
                throw new IllegalArgumentException("Línea " + line + ": máximo 3 decimales en cantidad y 2 en costo");
            }
            ProductVariant variant = variants.findById(input.variantId())
                    .orElseThrow(() -> new EntityNotFoundException("Variante no encontrada en línea " + line));
            if (!variant.active || !variant.product.active)
                throw new IllegalArgumentException("Línea " + line + ": variante o producto inactivo");
            PurchaseOrderLine detail = new PurchaseOrderLine();
            detail.purchaseOrder = order;
            detail.variant = variant;
            detail.quantity = input.quantity();
            detail.unitCost = input.unitCost();
            detail.total = input.quantity().multiply(input.unitCost()).setScale(2, java.math.RoundingMode.HALF_UP);
            order.lines.add(detail);
            order.total = order.total.add(detail.total);
        }
        orders.saveAndFlush(order);
        order.number = "OC-" + String.format("%08d", order.id);
        orders.saveAndFlush(order);
        audit.log("PURCHASE_ORDER_CREATE", "PurchaseOrder", order.id,
                "PENDING_APPROVAL usuario=" + username() + " fecha=" + order.orderedAt, null);
        return map(order);
    }

    @Transactional(readOnly = true)
    public Response findById(Long id) {
        return map(orders.findById(id).orElseThrow(() -> new EntityNotFoundException("Orden no encontrada")));
    }

    @Transactional
    public Response approve(Long id) {
        return decide(id, null);
    }

    @Transactional
    public Response reject(Long id, String reason) {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("El motivo de rechazo es obligatorio");
        if (reason.trim().length() > 500) throw new IllegalArgumentException("El motivo supera 500 caracteres");
        return decide(id, reason.trim());
    }

    private Response decide(Long id, String rejectionReason) {
        PurchaseOrder order = orders.findForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada"));
        if (order.status != PurchaseOrderStatus.PENDING_APPROVAL) {
            throw new BusinessConflictException("La orden ya fue decidida: " + order.status);
        }
        PurchaseOrderStatus previous = order.status;
        order.status = rejectionReason == null ? PurchaseOrderStatus.APPROVED : PurchaseOrderStatus.REJECTED;
        order.decidedAt = Instant.now();
        order.decidedBy = username();
        order.rejectionReason = rejectionReason;
        audit.log("PURCHASE_ORDER_DECISION", "PurchaseOrder", order.id,
                previous + " -> " + order.status + " usuario=" + order.decidedBy
                        + " fecha=" + order.decidedAt + " motivo=" + rejectionReason, null);
        orders.saveAndFlush(order);
        return map(order);
    }

    private Response map(PurchaseOrder order) {
        return new Response(order.id, order.number, order.supplier.id, order.orderedAt,
                order.status, order.total, order.decidedAt, order.decidedBy, order.rejectionReason,
                order.lines.stream().map(line -> new LineResponse(line.id, line.variant.id,
                        line.quantity, line.receivedQuantity, line.unitCost, line.total)).toList());
    }

    private String username() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "sistema" : auth.getName();
    }


    @Transactional(readOnly = true)
    public Page<Response> search(
            Long supplierId,
            PurchaseOrderStatus status,
            LocalDate from,
            LocalDate to,
            Long variantId,
            Pageable pageable) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "La fecha inicial no puede ser mayor que la fecha final"
            );
        }

        Specification<PurchaseOrder> specification = (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (supplierId != null) {
                predicates.add(
                        cb.equal(root.get("supplier").get("id"), supplierId)
                );
            }

            if (status != null) {
                predicates.add(
                        cb.equal(root.get("status"), status)
                );
            }

            if (from != null) {
                Instant fromInstant = from.atStartOfDay()
                        .toInstant(ZoneOffset.UTC);

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("orderedAt"), fromInstant
                        )
                );
            }

            if (to != null) {
                Instant toInstant = to.plusDays(1)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC);

                predicates.add(
                        cb.lessThan(root.get("orderedAt"), toInstant)
                );
            }

            if (variantId != null) {
                predicates.add(
                        cb.equal(
                                root.join("lines").get("variant").get("id"),
                                variantId
                        )
                );

                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return orders.findAll(specification, pageable)
                .map(this::map);
    }


}
