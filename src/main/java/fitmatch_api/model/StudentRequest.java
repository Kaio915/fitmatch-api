package fitmatch_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_requests")
public class StudentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long trainerId;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String dayName;

    @Column(nullable = false)
    private String time;

    @Column
    private String trainerName;

    // PENDING, APPROVED, REJECTED
    @Column(nullable = false)
    private String status = "PENDING";

    @Column
    private String planType = "DIARIO"; // DIARIO, SEMANAL, MENSAL

    @Column(columnDefinition = "TEXT")
    private String daysJson; // JSON array [{"dayName":"...","time":"..."}] para planos recorrentes

    @Column
    private Boolean hiddenForTrainer = false;

    @Column
    private Boolean hiddenForStudent = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime approvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTrainerId() { return trainerId; }
    public void setTrainerId(Long trainerId) { this.trainerId = trainerId; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getDayName() { return dayName; }
    public void setDayName(String dayName) { this.dayName = dayName; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getTrainerName() { return trainerName; }
    public void setTrainerName(String trainerName) { this.trainerName = trainerName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public String getDaysJson() { return daysJson; }
    public void setDaysJson(String daysJson) { this.daysJson = daysJson; }

    public Boolean getHiddenForTrainer() { return hiddenForTrainer; }
    public void setHiddenForTrainer(Boolean hiddenForTrainer) {
        this.hiddenForTrainer = hiddenForTrainer;
    }

    public Boolean getHiddenForStudent() { return hiddenForStudent; }
    public void setHiddenForStudent(Boolean hiddenForStudent) {
        this.hiddenForStudent = hiddenForStudent;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
}
