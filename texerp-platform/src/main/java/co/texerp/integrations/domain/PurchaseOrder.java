package co.texerp.integrations.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_order")
public class PurchaseOrder extends BaseEntity {
    @Column(length = 30, unique = true)
    public String number;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    public Supplier supplier;
    @Column(nullable = false)
    public Instant orderedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public PurchaseOrderStatus status = PurchaseOrderStatus.PENDING_APPROVAL;
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal total = BigDecimal.ZERO;
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    public List<PurchaseOrderLine> lines = new ArrayList<>();
    public Instant decidedAt;
    @Column(length = 255)
    public String decidedBy;
    @Column(length = 500)
    public String rejectionReason;
}
