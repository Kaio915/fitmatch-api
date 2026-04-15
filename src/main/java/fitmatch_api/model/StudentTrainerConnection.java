package fitmatch_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "student_trainer_connections",
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "trainer_id"})
)
public class StudentTrainerConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "trainer_id", nullable = false)
    private Long trainerId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "trainer_name")
    private String trainerName;

    @Column(name = "connected_at", updatable = false)
    private LocalDateTime connectedAt;

    @PrePersist
    protected void onCreate() {
        connectedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Long getTrainerId() { return trainerId; }
    public void setTrainerId(Long trainerId) { this.trainerId = trainerId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getTrainerName() { return trainerName; }
    public void setTrainerName(String trainerName) { this.trainerName = trainerName; }

    public LocalDateTime getConnectedAt() { return connectedAt; }
}
