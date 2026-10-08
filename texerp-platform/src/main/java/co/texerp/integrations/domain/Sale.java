package co.texerp.integrations.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sale", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sale_number", columnNames = "number"),
        @UniqueConstraint(name = "uk_sale_quotation", columnNames = "quotation_id")
})
public class Sale extends BaseEntity {
    @Column(name = "number", length = 30, unique = true)
    public String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_customer"))
    public Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_warehouse"))
    public Warehouse warehouse;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", unique = true, foreignKey = @ForeignKey(name = "fk_sale_quotation"))
    public Quotation quotation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public SaleStatus status = SaleStatus.CONFIRMED;

    @Column(name = "confirmed_at", nullable = false)
    public Instant confirmedAt;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 2)
    public BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 2)
    public BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    public List<SaleLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paidAt asc, id asc")
    public List<Payment> payments = new ArrayList<>();
}
