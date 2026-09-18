package fitmatch_api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Denúncia feita por um usuário (aluno ou personal) contra outro usuário.
 *
 * As denúncias ficam visíveis apenas para o admin na tela "Usuários Reportados".
 * O campo {@code seen} controla o "novo" (denúncia ainda não visualizada pelo
 * admin): a contagem de usuários reportados é zerada quando o admin abre a
 * lista, mas os registros permanecem até o usuário ser excluído.
 */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_reported", columnList = "reported_user_id, created_at"),
        @Index(name = "idx_reports_reporter", columnList = "reporter_id")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_user_id", nullable = false)
    private Long reportedUserId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean seen = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public void setReporterId(Long reporterId) {
        this.reporterId = reporterId;
    }

    public Long getReportedUserId() {
        return reportedUserId;
    }

    public void setReportedUserId(Long reportedUserId) {
        this.reportedUserId = reportedUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isSeen() {
        return seen;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }
}
