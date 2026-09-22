package fitmatch_api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Histórico de tentativas de cadastro (snapshot de cada evento terminal).
 * Cada registro representa uma tentativa que chegou a um estado final:
 * APPROVED (aprovado), REJECTED (rejeitado) ou DELETED (excluído pelo admin).
 *
 * Diferente da tabela "users" (que reutiliza a mesma linha quando o usuário
 * se cadastra novamente), esta tabela mantém todas as tentativas.
 */
@Entity
@Table(name = "user_history", indexes = {
        @Index(name = "idx_uh_type_recorded", columnList = "type, recorded_at"),
        @Index(name = "idx_uh_user", columnList = "user_id")
})
public class UserHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String name;

    private String email;

    private String cpf;

    @Enumerated(EnumType.STRING)
    private UserType type;

    // APPROVED, REJECTED ou DELETED
    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    // Última mensagem enviada pelo admin antes da rejeição (motivo complementar
    // exibido no chat quando o mesmo email se cadastra novamente).
    @Column(columnDefinition = "TEXT")
    private String lastAdminMessage;

    // Indica que esta tentativa corresponde a um usuário banido da plataforma.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean banned = false;

    // Motivo do banimento. É removido ao desbanir o usuário (o campo deixa de
    // ser exibido após o desbanimento).
    @Column(columnDefinition = "TEXT")
    private String bannedReason;

    // Indica que uma tentativa aprovada foi posteriormente excluída pelo admin.
    // O status permanece "APPROVED", mas o histórico exibe "Aprovado" (cinza)
    // ao lado de "Excluído" (vermelho).
    @Column(columnDefinition = "boolean default false")
    private boolean deleted = false;

    // Indica que este registro foi "limpo" da tela de histórico pelo admin.
    // O registro permanece no banco para preservar o contexto (motivos de
    // rejeição/exclusão) exibido no chat quando o mesmo email se cadastra de novo.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean hidden = false;

    // Data em que uma tentativa aprovada foi excluída pelo admin (soft delete).
    // Preenchida apenas quando deleted = true.
    private LocalDateTime deletedAt;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "photo", columnDefinition = "bytea")
    private byte[] photo;

    @Column(columnDefinition = "TEXT")
    private String objetivos;

    private String nivel;

    private String cref;

    private String cidade;

    private String especialidade;

    private String experiencia;

    private String valorHora;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // Data em que o cadastro foi criado (snapshot da tentativa).
    private LocalDateTime createdAt;

    // Data em que este registro do histórico foi gravado (evento terminal).
    @Column(name = "recorded_at", updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        recordedAt = LocalDateTime.now();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public UserType getType() {
        return type;
    }

    public void setType(UserType type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getLastAdminMessage() {
        return lastAdminMessage;
    }

    public void setLastAdminMessage(String lastAdminMessage) {
        this.lastAdminMessage = lastAdminMessage;
    }

    public boolean isBanned() {
        return banned;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }

    public String getBannedReason() {
        return bannedReason;
    }

    public void setBannedReason(String bannedReason) {
        this.bannedReason = bannedReason;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public byte[] getPhoto() {
        return photo;
    }

    public void setPhoto(byte[] photo) {
        this.photo = photo;
    }

    public String getObjetivos() {
        return objetivos;
    }

    public void setObjetivos(String objetivos) {
        this.objetivos = objetivos;
    }

    public String getNivel() {
        return nivel;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public String getCref() {
        return cref;
    }

    public void setCref(String cref) {
        this.cref = cref;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }

    public String getExperiencia() {
        return experiencia;
    }

    public void setExperiencia(String experiencia) {
        this.experiencia = experiencia;
    }

    public String getValorHora() {
        return valorHora;
    }

    public void setValorHora(String valorHora) {
        this.valorHora = valorHora;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
