package co.texerp.integrations.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_sale"))
    public Sale sale;

    @Column(nullable = false, length = 50)
    public String method;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    public Instant paidAt;

    @Column(length = 150)
    public String reference;
}
