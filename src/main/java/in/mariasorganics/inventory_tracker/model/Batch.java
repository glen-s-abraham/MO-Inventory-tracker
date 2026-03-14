package in.mariasorganics.inventory_tracker.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch")
public class Batch {

    public enum BatchStatus {
        ACTIVE, COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", unique = true, nullable = false)
    private String batchId;

    @Column(name = "inoculation_date", nullable = false)
    private LocalDate inoculationDate;

    @Column(name = "bag_count", nullable = false)
    private Integer bagCount;

    @Column(name = "target_exit_date", nullable = false)
    private LocalDate targetExitDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status = BatchStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Batch() {
        this.createdAt = LocalDateTime.now();
    }

    public Batch(String batchId, LocalDate inoculationDate, Integer bagCount, LocalDate targetExitDate) {
        this.batchId = batchId;
        this.inoculationDate = inoculationDate;
        this.bagCount = bagCount;
        this.targetExitDate = targetExitDate;
        this.status = BatchStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public LocalDate getInoculationDate() { return inoculationDate; }
    public void setInoculationDate(LocalDate inoculationDate) { this.inoculationDate = inoculationDate; }
    public Integer getBagCount() { return bagCount; }
    public void setBagCount(Integer bagCount) { this.bagCount = bagCount; }
    public LocalDate getTargetExitDate() { return targetExitDate; }
    public void setTargetExitDate(LocalDate targetExitDate) { this.targetExitDate = targetExitDate; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
