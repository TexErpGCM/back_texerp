
package co.texerp.integrations.etl.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "etl_job", schema = "texerp_dw")
public class EtlJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "allow_parallel", nullable = false)
    private Boolean allowParallel = false;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getAllowParallel() {
        return allowParallel;
    }

    public void setAllowParallel(Boolean allowParallel) {
        this.allowParallel = allowParallel;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
