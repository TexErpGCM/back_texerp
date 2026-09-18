package co.texerp.integrations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_balance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_balance_variant_warehouse",
                columnNames = {"variant_id", "warehouse_id"}
        )
)
public class InventoryBalance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "variant_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_balance_variant")
    )
    public ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "warehouse_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_balance_warehouse")
    )
    public Warehouse warehouse;

    @Column(name = "available_quantity", nullable = false, precision = 19, scale = 3)
    public BigDecimal availableQuantity = BigDecimal.ZERO;

    @Column(name = "reserved_quantity", nullable = false, precision = 19, scale = 3)
    public BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "minimum_quantity", nullable = false, precision = 19, scale = 3)
    public BigDecimal minimumQuantity = BigDecimal.ZERO;
}
