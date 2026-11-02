
package co.texerp.integrations.etl.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "etl_execution", schema = "texerp_dw")
public class EtlExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private EtlJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_execution_id")
    private EtlExecution previousExecution;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "watermark_from")
    private OffsetDateTime watermarkFrom;

    @Column(name = "watermark_to")
    private OffsetDateTime watermarkTo;

    @Column(name = "records_read", nullable = false)
    private Long recordsRead = 0L;

    @Column(name = "records_written", nullable = false)
    private Long recordsWritten = 0L;

    @Column(name = "records_rejected", nullable = false)
    private Long recordsRejected = 0L;

    @Column(name = "failed_step", length = 100)
    private String failedStep;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EtlJob getJob() { return job; }
    public void setJob(EtlJob job) { this.job = job; }

    public EtlExecution getPreviousExecution() { return previousExecution; }
    public void setPreviousExecution(EtlExecution previousExecution) {
        this.previousExecution = previousExecution;
    }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public OffsetDateTime getWatermarkFrom() { return watermarkFrom; }
    public void setWatermarkFrom(OffsetDateTime watermarkFrom) {
        this.watermarkFrom = watermarkFrom;
    }

    public OffsetDateTime getWatermarkTo() { return watermarkTo; }
    public void setWatermarkTo(OffsetDateTime watermarkTo) {
        this.watermarkTo = watermarkTo;
    }

    public Long getRecordsRead() { return recordsRead; }
    public void setRecordsRead(Long recordsRead) {
        this.recordsRead = recordsRead;
    }

    public Long getRecordsWritten() { return recordsWritten; }
    public void setRecordsWritten(Long recordsWritten) {
        this.recordsWritten = recordsWritten;
    }

    public Long getRecordsRejected() { return recordsRejected; }
    public void setRecordsRejected(Long recordsRejected) {
        this.recordsRejected = recordsRejected;
    }

    public String getFailedStep() { return failedStep; }
    public void setFailedStep(String failedStep) {
        this.failedStep = failedStep;
    }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
