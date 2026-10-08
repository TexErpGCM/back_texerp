package co.texerp.integrations.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotation", uniqueConstraints = @UniqueConstraint(name = "uk_quotation_number", columnNames = "number"))
public class Quotation extends BaseEntity {
    @Column(name = "number", length = 30, unique = true)
    public String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_quotation_customer"))
    public Customer customer;

    @Column(name = "issue_date", nullable = false)
    public LocalDate issueDate;

    @Column(name = "expiration_date", nullable = false)
    public LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public QuotationStatus status = QuotationStatus.DRAFT;

    @Column(length = 1000)
    public String conditions;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    public List<QuotationLine> lines = new ArrayList<>();
}
