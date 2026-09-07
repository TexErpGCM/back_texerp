package co.texerp.integrations.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variant", uniqueConstraints =
@UniqueConstraint(name = "uk_product_variant_sku", columnNames = "sku"))
public class ProductVariant extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_variant_product"))
    public Product product;

    @Column(nullable = false, length = 60)
    public String sku;

    @Column(nullable = false, length = 80)
    public String color;

    @Column(length = 120)
    public String pattern;

    @Column(nullable = false, precision = 12, scale = 3)
    public BigDecimal width;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_variant_unit"))
    public UnitOfMeasure unit;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal cost;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 2)
    public BigDecimal salePrice;

    @Column(nullable = false)
    public boolean active = true;
}
