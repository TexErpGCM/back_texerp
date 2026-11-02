package co.texerp.integrations.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "purchase_receipt_line")
public class PurchaseReceiptLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    public PurchaseReceipt receipt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_line_id", nullable = false)
    public PurchaseOrderLine orderLine;
    @Column(nullable = false, precision = 19, scale = 3)
    public BigDecimal quantity;
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal unitCost;
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal total;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_movement_id", nullable = false)
    public InventoryMovement movement;
}
