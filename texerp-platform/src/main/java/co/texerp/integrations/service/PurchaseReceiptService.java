package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.*;
import co.texerp.integrations.dto.PurchaseReceiptDtos.*;
import co.texerp.integrations.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;

@Service
public class PurchaseReceiptService {
    private final PurchaseOrderRepository orders;
    private final PurchaseReceiptRepository receipts;
    private final WarehouseService warehouses;
    private final InventoryBalanceUpdater inventory;
    private final AuditService audit;

    public PurchaseReceiptService(PurchaseOrderRepository orders, PurchaseReceiptRepository receipts,
                                  WarehouseService warehouses, InventoryBalanceUpdater inventory, AuditService audit) {
        this.orders = orders;
        this.receipts = receipts;
        this.warehouses = warehouses;
        this.inventory = inventory;
        this.audit = audit;
    }

    @Transactional
    public Response receive(Long orderId, CreateRequest request) {
        PurchaseOrder order = orders.findForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada"));
        if (order.status != PurchaseOrderStatus.APPROVED && order.status != PurchaseOrderStatus.PARTIALLY_RECEIVED)
            throw new BusinessConflictException("La orden no admite recepciones en estado " + order.status);
        Warehouse warehouse = warehouses.requireActive(request.warehouseId());
        if (request.document().trim().length() > 255)
            throw new IllegalArgumentException("El documento supera 255 caracteres");
        HashSet<Long> lineIds = new HashSet<>();
        // Validar todas las líneas antes de generar entradas al inventario.
        for (int i = 0; i < request.lines().size(); i++) {
            LineRequest input = request.lines().get(i);
            PurchaseOrderLine line = findLine(order, input.orderLineId(), i + 1);
            if (!lineIds.add(line.id)) throw new IllegalArgumentException("Línea " + (i + 1) + ": duplicada");
            if (input.quantity() == null || input.quantity().signum() <= 0 || input.quantity().scale() > 3)
                throw new IllegalArgumentException("Línea " + (i + 1) + ": cantidad inválida");
            BigDecimal pending = line.quantity.subtract(line.receivedQuantity);
            if (input.quantity().compareTo(pending) > 0)
                throw new IllegalArgumentException("Línea " + (i + 1) + " (" + line.id + "): supera pendiente " + pending);
        }
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.purchaseOrder = order;
        receipt.warehouse = warehouse;
        receipt.document = request.document().trim();
        receipt.receivedAt = Instant.now();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        receipt.receivedBy = authentication == null ? "sistema" : authentication.getName();
        // La numeración deriva del ID persistido, dentro de la misma transacción.
        receipt.number = "TMP-" + java.util.UUID.randomUUID().toString().substring(0, 24);
        receipts.saveAndFlush(receipt);
        receipt.number = "REC-" + String.format("%08d", receipt.id);
        for (LineRequest input : request.lines()) {
            PurchaseOrderLine line = findLine(order, input.orderLineId(), 0);
            var movement = inventory.applyConfirmedMovement(line.variant.id, warehouse.id,
                    InventoryMovementType.RECEIPT, input.quantity(), BigDecimal.ZERO, receipt.number);
            PurchaseReceiptLine detail = new PurchaseReceiptLine();
            detail.receipt = receipt;
            detail.orderLine = line;
            detail.quantity = input.quantity();
            detail.unitCost = line.unitCost;
            detail.total = input.quantity().multiply(line.unitCost).setScale(2, java.math.RoundingMode.HALF_UP);
            detail.movement = movement.movement();
            receipt.lines.add(detail);
            line.receivedQuantity = line.receivedQuantity.add(input.quantity());
            audit.log("INVENTORY_RECEIPT", "InventoryMovement", movement.movement().id,
                    "Orden=" + order.id + ", recepción=" + receipt.id + ", variante=" + line.variant.id
                            + ", antes=" + movement.movement().previousAvailable
                            + ", después=" + movement.movement().newAvailable, null);
        }
        PurchaseOrderStatus previous = order.status;
        order.status = order.lines.stream().allMatch(l -> l.receivedQuantity.compareTo(l.quantity) >= 0)
                ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
        receipts.saveAndFlush(receipt);
        orders.saveAndFlush(order);
        audit.log("PURCHASE_RECEIPT", "PurchaseReceipt", receipt.id,
                "Orden=" + order.id + ", estado anterior=" + previous + ", nuevo=" + order.status
                        + ", bodega=" + warehouse.id + ", documento=" + receipt.document, null);
        return map(receipt);
    }

    @Transactional(readOnly = true)
    public Response findById(Long id) {
        return map(receipts.findById(id).orElseThrow(() -> new EntityNotFoundException("Recepción no encontrada")));
    }

    @Transactional(readOnly = true)
    public java.util.List<Response> byOrder(Long orderId) {
        if (!orders.existsById(orderId)) throw new EntityNotFoundException("Orden no encontrada");
        return receipts.findByPurchaseOrderIdOrderByReceivedAtDesc(orderId).stream().map(this::map).toList();
    }

    private PurchaseOrderLine findLine(PurchaseOrder order, Long id, int position) {
        return order.lines.stream().filter(l -> l.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Línea " + position + ": no pertenece a la orden"));
    }

    private Response map(PurchaseReceipt receipt) {
        return new Response(receipt.id, receipt.number, receipt.receivedAt, receipt.purchaseOrder.id,
                receipt.warehouse.id, receipt.document, receipt.receivedBy,
                receipt.lines.stream().map(l -> new LineResponse(l.id, l.orderLine.id, l.orderLine.variant.id,
                        l.quantity, l.unitCost, l.total, l.movement.id)).toList());
    }
}
