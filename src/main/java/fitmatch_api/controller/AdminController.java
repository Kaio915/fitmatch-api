package fitmatch_api.controller;

import fitmatch_api.model.ChatMessage;
import fitmatch_api.model.User;
import fitmatch_api.model.UserHistory;
import fitmatch_api.model.UserStatus;
import fitmatch_api.model.UserType;
import fitmatch_api.repository.ChatMessageRepository;
import fitmatch_api.repository.UserHistoryRepository;
import fitmatch_api.repository.UserRepository;
import fitmatch_api.security.AuthContext;
import fitmatch_api.service.EmailService;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

        private static final String ADMIN_DELETED_REASON = "Conta excluída pelo administrador";

    private final UserRepository repo;
    private final ChatMessageRepository chatMessageRepo;
    private final UserHistoryRepository historyRepo;
    private final EmailService emailService;
    private final Environment environment;

    public AdminController(UserRepository repo, ChatMessageRepository chatMessageRepo, UserHistoryRepository historyRepo, EmailService emailService, Environment environment) {
        this.repo = repo;
        this.chatMessageRepo = chatMessageRepo;
        this.historyRepo = historyRepo;
        this.emailService = emailService;
        this.environment = environment;
    }

    // ================= CPF MASK =================

    private static String maskCpf(String cpf) {

        if (cpf == null || cpf.length() != 11)
            return cpf;

        return cpf.substring(0, 3)
                + ".***.***-"
                + cpf.substring(9);
    }

    // ================= FOTO BASE64 =================

    private static String photoToBase64(byte[] photo) {

        if (photo == null || photo.length == 0)
            return null;

        return Base64.getEncoder().encodeToString(photo);
    }

    // ================= DTO =================

    public record AdminUserResponse(
            Long id,
            String name,
            String email,
            String type,
            String status,
            String cpf,
            String photoBase64,
            String objetivos,
            String nivel,
            String cref,
            String cidade,
            String especialidade,
            String experiencia,
            String valorHora,
            String bio,
            LocalDateTime createdAt,
            String rejectionReason
    ) {

        public static AdminUserResponse from(User u) {

            String reason = u.getRejectionReason();
            boolean deletedByAdmin = reason != null
                    && reason.trim().equalsIgnoreCase(ADMIN_DELETED_REASON);
            String statusLabel = deletedByAdmin
                    ? "DELETED"
                    : (u.getStatus() == null ? null : u.getStatus().name());

            return new AdminUserResponse(

                    u.getId(),

                    u.getName(),

                    u.getEmail(),

                    u.getType() == null
                            ? null
                            : u.getType().name().toLowerCase(),

                    statusLabel,

                    maskCpf(u.getCpf()),

                    photoToBase64(u.getPhoto()),

                    u.getObjetivos(),

                    u.getNivel(),

                    u.getCref(),

                    u.getCidade(),

                    u.getEspecialidade(),

                    u.getExperiencia(),

                    u.getValorHora(),

                    u.getBio(),

                    u.getCreatedAt(),

                    u.getRejectionReason()
            );
        }

        public static AdminUserResponse from(UserHistory h) {
            return new AdminUserResponse(
                    // Mantém o id do USUÁRIO (e não o id do registro do histórico)
                    // para que as ações do frontend (ex.: DELETE /admin/users/{id})
                    // continuem funcionando como antes.
                    h.getUserId(),
                    h.getName(),
                    h.getEmail(),
                    h.getType() == null
                            ? null
                            : h.getType().name().toLowerCase(),
                    h.getStatus(),
                    maskCpf(h.getCpf()),
                    photoToBase64(h.getPhoto()),
                    h.getObjetivos(),
                    h.getNivel(),
                    h.getCref(),
                    h.getCidade(),
                    h.getEspecialidade(),
                    h.getExperiencia(),
                    h.getValorHora(),
                    h.getBio(),
                    h.getCreatedAt(),
                    h.getRejectionReason()
            );
        }
    }

    // ================= PENDING =================

    @GetMapping("/pending/{type}")
    public List<AdminUserResponse> getPending(
            @PathVariable UserType type
    ) {

        AuthContext.requireRole("ADMIN");

        return repo
                .findByTypeAndStatusInOrderByCreatedAtDesc(
                        type,
                        List.of(
                                UserStatus.PENDING,
                                UserStatus.TEMPORARILY_REJECTED
                        )
                )
                .stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    // ================= APPROVE =================

    @PutMapping("/approve/{id}")
    public void approve(@PathVariable Long id) {

        AuthContext.requireRole("ADMIN");

        User user = repo
                .findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Usuário não encontrado"
                        )
                );

        user.setStatus(UserStatus.APPROVED);

        user.setRejectionReason(null);

        repo.save(user);
        recordHistory(user, "APPROVED");

        emailService.sendApprovalEmail(user);
    }

    // ================= REJECT =================

    @PutMapping("/reject/{id}")
    public void reject(
            @PathVariable Long id,
            @RequestBody(required = false)
            Map<String, String> body
    ) {

        AuthContext.requireRole("ADMIN");

        Long adminId = AuthContext.requirePrincipal().userId();

        User user = repo
                .findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Usuário não encontrado"
                        )
                );

        String reason =
                body != null
                        ? body.get("reason")
                        : null;

        user.setStatus(UserStatus.REJECTED);

        user.setRejectionReason(reason);

        repo.save(user);
        recordHistory(user, "REJECTED");

        emailService.sendRejectionEmail(user, reason);

        chatMessageRepo.deleteConversation(adminId, user.getId());
    }

    // ================= TEMPORARY REJECT =================

    @PutMapping("/temporary-reject/{id}")
    public void temporarilyReject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        AuthContext.requireRole("ADMIN");

        User user = repo
                .findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Usuário não encontrado"
                        )
                );

        if (user.getType() == UserType.admin) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não é possível rejeitar temporariamente um administrador");
        }

        if (user.getStatus() != UserStatus.PENDING
                && user.getStatus() != UserStatus.TEMPORARILY_REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A rejeição temporária é permitida apenas para usuários pendentes");
        }

        Long adminId = AuthContext.requirePrincipal().userId();

        // 1) Motivo enviado pelo frontend tem prioridade.
        String reason = body != null ? body.get("reason") : null;

        // 2) Fallback: última mensagem enviada pelo admin para o usuário.
        if (reason == null || reason.isBlank()) {
            reason = chatMessageRepo
                    .findTopBySenderIdAndReceiverIdOrderBySentAtDesc(adminId, user.getId())
                    .map(ChatMessage::getText)
                    .orElse(null);
        }

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Envie uma mensagem antes de rejeitar temporariamente");
        }

        user.setStatus(UserStatus.TEMPORARILY_REJECTED);
        user.setRejectionReason(reason);
        repo.save(user);
    }

    // ================= TEST EMAIL =================

    @PostMapping("/test-email")
    public Map<String, Object> testEmail(
            @RequestBody(required = false)
            Map<String, String> body
    ) {

        AuthContext.requireRole("ADMIN");

        String to = body != null ? body.get("email") : null;

        if (to == null || to.isBlank()) {
            Long adminId = AuthContext.requirePrincipal().userId();
            to = repo.findById(adminId).map(User::getEmail).orElse(null);
        }

        if (to == null || to.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe um e-mail de destino (ex.: {\"email\": \"destino@gmail.com\"})"
            );
        }

        boolean sent = emailService.sendTestEmail(to);

        if (!sent) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Falha ao enviar o e-mail de teste para " + to + ". Veja os logs do servidor."
            );
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sent", true);
        response.put("to", to);
        response.put("message", "E-mail de teste enviado com sucesso");
        return response;
    }

    // ================= MAIL STATUS (diagnóstico) =================

    @GetMapping("/mail-status")
    public Map<String, Object> mailStatus() {
        AuthContext.requireRole("ADMIN");

        Map<String, Object> m = new HashMap<>();
        m.put("env.MAIL_HOST", System.getenv("MAIL_HOST"));
        m.put("env.MAIL_USERNAME", System.getenv("MAIL_USERNAME"));
        m.put("env.MAIL_FROM", System.getenv("MAIL_FROM"));
        m.put("spring.mail.host", environment.getProperty("spring.mail.host"));
        m.put("spring.mail.port", environment.getProperty("spring.mail.port"));
        m.put("spring.mail.username", environment.getProperty("spring.mail.username"));
        m.put("app.mail.from", environment.getProperty("app.mail.from"));
        m.put("app.mail.enabled", environment.getProperty("app.mail.enabled"));
        return m;
    }

        @DeleteMapping("/users/{id}")
        public void deleteUser(@PathVariable Long id) {

                AuthContext.requireRole("ADMIN");

                Long adminId = AuthContext.requirePrincipal().userId();

                User user = repo
                                .findById(id)
                                .orElseThrow(() ->
                                                new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "Usuário não encontrado"
                                                )
                                );

                if (user.getType() == UserType.admin) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Não é permitido excluir conta de admin");
                }

                // Se o cadastro já havia sido rejeitado antes, o usuário já foi
                // notificado por e-mail; não é necessário enviar outro aviso de exclusão.
                boolean wasRejected = user.getStatus() == UserStatus.REJECTED;

                user.setStatus(UserStatus.REJECTED);
                user.setRejectionReason(ADMIN_DELETED_REASON);
                repo.save(user);
                recordHistory(user, "DELETED");

                if (!wasRejected) {
                        emailService.sendAccountDeletedEmail(user);
                }

                chatMessageRepo.deleteConversation(adminId, user.getId());
        }

    // ================= HISTORY =================

    @GetMapping("/users/{type}")
    public List<AdminUserResponse> getUsersHistory(
            @PathVariable UserType type,
            @RequestParam(required = false)
            String status
    ) {

        AuthContext.requireRole("ADMIN");

        List<UserHistory> history;

        if (status != null && !status.isBlank()) {
            history = historyRepo.findByTypeAndStatusOrderByRecordedAtDesc(
                    type,
                    status.trim().toUpperCase()
            );
        } else {
            history = historyRepo.findByTypeOrderByRecordedAtDesc(type);
        }

        return history
                .stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    // Registra no histórico cada tentativa terminal (aprovado/rejeitado/excluído),
    // preservando um snapshot mesmo quando a linha de "users" é reutilizada em um
    // novo cadastro.
    private void recordHistory(User user, String status) {
        UserHistory h = new UserHistory();
        h.setUserId(user.getId());
        h.setName(user.getName());
        h.setEmail(user.getEmail());
        h.setCpf(user.getCpf());
        h.setType(user.getType());
        h.setStatus(status);
        h.setRejectionReason(user.getRejectionReason());
        h.setPhoto(user.getPhoto());
        h.setObjetivos(user.getObjetivos());
        h.setNivel(user.getNivel());
        h.setCref(user.getCref());
        h.setCidade(user.getCidade());
        h.setEspecialidade(user.getEspecialidade());
        h.setExperiencia(user.getExperiencia());
        h.setValorHora(user.getValorHora());
        h.setBio(user.getBio());
        h.setCreatedAt(user.getCreatedAt());
        historyRepo.save(h);
    }

}