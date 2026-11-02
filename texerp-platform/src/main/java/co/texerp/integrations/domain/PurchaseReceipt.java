package co.texerp.integrations.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_receipt")
public class PurchaseReceipt extends BaseEntity {
    @Column(nullable = false, unique = true, length = 30)
    public String number;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    public PurchaseOrder purchaseOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    public Warehouse warehouse;
    @Column(nullable = false, length = 255)
    public String document;
    @Column(nullable = false)
    public Instant receivedAt = Instant.now();
    @Column(nullable = false, length = 255)
    public String receivedBy;
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<PurchaseReceiptLine> lines = new ArrayList<>();
}
