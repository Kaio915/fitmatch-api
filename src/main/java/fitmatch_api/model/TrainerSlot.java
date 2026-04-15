package fitmatch_api.model;

import jakarta.persistence.*;

@Entity
@Table(
    name = "trainer_slots",
    uniqueConstraints = @UniqueConstraint(columnNames = {"trainerId", "dayName", "time"})
)
public class TrainerSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long trainerId;

    @Column(nullable = false)
    private String dayName;

    @Column(nullable = false)
    private String time;

    // "BLOCKED" - único estado persistido; disponível = linha ausente
    @Column(nullable = false)
    private String state = "BLOCKED";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTrainerId() { return trainerId; }
    public void setTrainerId(Long trainerId) { this.trainerId = trainerId; }

    public String getDayName() { return dayName; }
    public void setDayName(String dayName) { this.dayName = dayName; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
}
