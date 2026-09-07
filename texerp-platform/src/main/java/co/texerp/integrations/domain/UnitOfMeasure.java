package co.texerp.integrations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "unit_of_measure", uniqueConstraints =
@UniqueConstraint(name = "uk_unit_of_measure_code", columnNames = "code"))
public class UnitOfMeasure extends BaseEntity {
    @Column(nullable = false, length = 20)
    public String code;

    @Column(nullable = false, length = 100)
    public String name;

    @Column(nullable = false)
    public boolean active = true;
}
