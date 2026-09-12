package co.texerp.integrations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "warehouse", uniqueConstraints =
@UniqueConstraint(name = "uk_warehouse_code", columnNames = "code"))
public class Warehouse extends BaseEntity {

    @Column(nullable = false, length = 50)
    public String code;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(nullable = false, length = 200)
    public String location;

    @Column(nullable = false)
    public boolean active = true;
}
