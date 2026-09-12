package co.texerp.integrations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "supplier", uniqueConstraints =
@UniqueConstraint(name = "uk_supplier_tax_id", columnNames = "tax_id"))
public class Supplier extends BaseEntity {

    @Column(name = "tax_id", nullable = false, length = 50)
    public String taxId;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(nullable = false, length = 30)
    public String phone;

    @Column(nullable = false, length = 150)
    public String email;

    @Column(nullable = false)
    public boolean active = true;
}
