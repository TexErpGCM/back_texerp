package co.texerp.integrations.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_line")
public class SaleLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_line_sale"))
    public Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_line_variant"))
    public ProductVariant variant;

    @Column(nullable = false, precision = 19, scale = 3)
    public BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    public BigDecimal unitPrice;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 2)
    public BigDecimal unitCost;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    public BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 9, scale = 4)
    public BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    public BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal subtotal;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal total;
}
