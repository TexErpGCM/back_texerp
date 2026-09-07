package co.texerp.integrations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "customer", uniqueConstraints =
@UniqueConstraint(name = "uk_customer_document", columnNames = "document"))
public class Customer extends BaseEntity {

    @Column(nullable = false, length = 50)
    public String document;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(nullable = false, length = 50)
    public String type;

    @Column(length = 150)
    public String email;

    @Column(length = 30)
    public String phone;

    @Column(length = 80)
    public String classification;

    @Column(nullable = false)
    public boolean active = true;
}
