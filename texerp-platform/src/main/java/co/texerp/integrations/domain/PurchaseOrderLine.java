package co.texerp.integrations.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_line")
public class PurchaseOrderLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    public PurchaseOrder purchaseOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    public ProductVariant variant;
    @Column(nullable = false, precision = 19, scale = 3)
    public BigDecimal quantity;
    @Column(nullable = false, precision = 19, scale = 3)
    public BigDecimal receivedQuantity = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal unitCost;
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal total;
}
