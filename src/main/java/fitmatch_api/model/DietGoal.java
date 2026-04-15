package fitmatch_api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "diet_goals",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId"})
)
public class DietGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Double basalKcal;

    @Column(nullable = false)
    private Double targetKcal;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Double getBasalKcal() {
        return basalKcal;
    }

    public void setBasalKcal(Double basalKcal) {
        this.basalKcal = basalKcal;
    }

    public Double getTargetKcal() {
        return targetKcal;
    }

    public void setTargetKcal(Double targetKcal) {
        this.targetKcal = targetKcal;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
