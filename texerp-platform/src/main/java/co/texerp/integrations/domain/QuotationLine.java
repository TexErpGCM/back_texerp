package co.texerp.integrations.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "quotation_line")
public class QuotationLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_quotation_line_quotation"))
    public Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_quotation_line_variant"))
    public ProductVariant variant;

    @Column(nullable = false, precision = 19, scale = 3)
    public BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    public BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal subtotal;
}
