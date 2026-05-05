package fitmatch_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_cm_sr_time", columnList = "sender_id, receiver_id, sent_at"),
    @Index(name = "idx_cm_rs_time", columnList = "receiver_id, sender_id, sent_at")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "is_termination", nullable = false)
    private boolean termination = false;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public LocalDateTime getSentAt() { return sentAt; }

    public boolean isTermination() { return termination; }
    public void setTermination(boolean termination) { this.termination = termination; }
}
