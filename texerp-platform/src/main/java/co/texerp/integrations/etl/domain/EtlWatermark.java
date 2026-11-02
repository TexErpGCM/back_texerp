
package co.texerp.integrations.etl.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "etl_watermark", schema = "texerp_dw")
public class EtlWatermark {

    @Id
    @Column(name = "job_id")
    private Long jobId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "job_id")
    private EtlJob job;

    @Column(name = "last_successful_watermark")
    private OffsetDateTime lastSuccessfulWatermark;

    @Column(name = "updated_at", nullable = false,
            insertable = false)
    private OffsetDateTime updatedAt;

    public Long getJobId() { return jobId; }

    public EtlJob getJob() { return job; }
    public void setJob(EtlJob job) { this.job = job; }

    public OffsetDateTime getLastSuccessfulWatermark() {
        return lastSuccessfulWatermark;
    }

    public void setLastSuccessfulWatermark(
            OffsetDateTime lastSuccessfulWatermark) {
        this.lastSuccessfulWatermark = lastSuccessfulWatermark;
    }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
