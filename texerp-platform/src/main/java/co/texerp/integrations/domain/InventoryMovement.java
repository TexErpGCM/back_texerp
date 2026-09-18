package co.texerp.integrations.domain;

import co.texerp.integrations.exception.ImmutableInventoryMovementException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "inventory_movement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_movement_compensates",
                columnNames = "compensates_movement_id"
        )
)
public class InventoryMovement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "variant_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_movement_variant")
    )
    public ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "warehouse_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_movement_warehouse")
    )
    public Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 40)
    public InventoryMovementType type;

    /**
     * Cambio firmado del saldo disponible.
     * Ej.: recepción +10, venta -3, ajuste +/-N.
     */
    @Column(name = "quantity", nullable = false, updatable = false, precision = 19, scale = 3)
    public BigDecimal quantity;

    /**
     * Cambio firmado del saldo reservado, cuando el movimiento también lo afecta.
     */
    @Column(name = "reserved_delta", nullable = false, updatable = false, precision = 19, scale = 3)
    public BigDecimal reservedDelta = BigDecimal.ZERO;

    @Column(name = "previous_available", nullable = false, updatable = false, precision = 19, scale = 3)
    public BigDecimal previousAvailable;

    @Column(name = "new_available", nullable = false, updatable = false, precision = 19, scale = 3)
    public BigDecimal newAvailable;

    @Column(name = "previous_reserved", nullable = false, updatable = false, precision = 19, scale = 3)
    public BigDecimal previousReserved;

    @Column(name = "new_reserved", nullable = false, updatable = false, precision = 19, scale = 3)
    public BigDecimal newReserved;

    @Column(name = "source_document", nullable = false, updatable = false, length = 255)
    public String sourceDocument;

    @Column(name = "performed_by", nullable = false, updatable = false, length = 255)
    public String performedBy;

    @Column(name = "movement_at", nullable = false, updatable = false)
    public Instant movementAt;

    @Column(name = "reason", updatable = false, length = 500)
    public String reason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "compensates_movement_id",
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_movement_compensates")
    )
    public InventoryMovement compensatesMovement;

    @PrePersist
    protected void validateTraceability() {
        if (quantity == null || reservedDelta == null
                || previousAvailable == null || newAvailable == null
                || previousReserved == null || newReserved == null) {
            throw new IllegalArgumentException("El movimiento debe conservar todos sus saldos y cantidades");
        }
        if (newAvailable.subtract(previousAvailable).compareTo(quantity) != 0) {
            throw new IllegalArgumentException("El saldo disponible del movimiento no corresponde con su cantidad");
        }
        if (newReserved.subtract(previousReserved).compareTo(reservedDelta) != 0) {
            throw new IllegalArgumentException("El saldo reservado del movimiento no corresponde con su variación");
        }
        if (type == InventoryMovementType.SALE && quantity.signum() >= 0) {
            throw new IllegalArgumentException("Una venta debe disminuir el saldo disponible");
        }
        if (type == InventoryMovementType.RECEIPT && quantity.signum() <= 0) {
            throw new IllegalArgumentException("Una recepción debe aumentar el saldo disponible");
        }
    }

    @PreUpdate
    protected void rejectUpdate() {
        throw new ImmutableInventoryMovementException();
    }

    @PreRemove
    protected void rejectDelete() {
        throw new ImmutableInventoryMovementException();
    }
}
