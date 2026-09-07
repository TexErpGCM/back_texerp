package co.texerp.integrations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "product", uniqueConstraints =
@UniqueConstraint(name = "uk_product_code", columnNames = "code"))
public class Product extends BaseEntity {

    @Column(nullable = false, length = 50)
    public String code;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(name = "fabric_type", nullable = false, length = 100)
    public String fabricType;

    @Column(nullable = false, length = 500)
    public String composition;

    @Column(nullable = false)
    public boolean active = true;
}
