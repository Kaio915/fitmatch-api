package fitmatch_api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_cpf", columnNames = "cpf")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ================= BÁSICOS =================

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private UserType type;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    // ================= CPF =================

    @Column(nullable = false, length = 11)
    private String cpf;

    // ================= FOTO (BYTEA - CORRETO PARA POSTGRES) =================
    // ❗ NÃO usar @Lob no Postgres quando for bytea
    // Isso evita o erro: "expression is of type bigint"

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "photo", columnDefinition = "bytea")
    @JsonIgnore
    private byte[] photo;

    // ================= REJEIÇÃO =================

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    // ================= CAMPOS DO ALUNO =================

    @Column(columnDefinition = "TEXT")
    private String objetivos;

    private String nivel;

    // ================= CAMPOS DO PERSONAL =================

    private String cref;

    private String cidade;

    private String especialidade;

    private String experiencia;

    private String valorHora;

    private String horasPorSessao;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // ================= DATA =================

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastOnlineAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ================= GETTERS =================

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    public UserType getType() {
        return type;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getCpf() {
        return cpf;
    }

    @JsonIgnore
    public byte[] getPhoto() {
        return photo;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getObjetivos() {
        return objetivos;
    }

    public String getNivel() {
        return nivel;
    }

    public String getCref() {
        return cref;
    }

    public String getCidade() {
        return cidade;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public String getExperiencia() {
        return experiencia;
    }

    public String getValorHora() {
        return valorHora;
    }

    public String getHorasPorSessao() {
        return horasPorSessao;
    }

    public String getBio() {
        return bio;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastOnlineAt() {
        return lastOnlineAt;
    }

    // ================= SETTERS =================

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setType(UserType type) {
        this.type = type;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public void setPhoto(byte[] photo) {
        this.photo = photo;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public void setObjetivos(String objetivos) {
        this.objetivos = objetivos;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public void setCref(String cref) {
        this.cref = cref;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }

    public void setExperiencia(String experiencia) {
        this.experiencia = experiencia;
    }

    public void setValorHora(String valorHora) {
        this.valorHora = valorHora;
    }

    public void setHorasPorSessao(String horasPorSessao) {
        this.horasPorSessao = horasPorSessao;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setLastOnlineAt(LocalDateTime lastOnlineAt) {
        this.lastOnlineAt = lastOnlineAt;
    }
}