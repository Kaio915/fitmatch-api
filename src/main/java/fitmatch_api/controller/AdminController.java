package fitmatch_api.controller;

import fitmatch_api.model.ChatMessage;
import fitmatch_api.model.User;
import fitmatch_api.model.UserStatus;
import fitmatch_api.model.UserType;
import fitmatch_api.repository.ChatMessageRepository;
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
    private final EmailService emailService;
    private final Environment environment;

    public AdminController(UserRepository repo, ChatMessageRepository chatMessageRepo, EmailService emailService, Environment environment) {
        this.repo = repo;
        this.chatMessageRepo = chatMessageRepo;
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

        emailService.sendRejectionEmail(user, reason);

        chatMessageRepo.deleteConversation(adminId, user.getId());
    }

    // ================= TEMPORARY REJECT =================

    @PutMapping("/temporary-reject/{id}")
    public void temporarilyReject(@PathVariable Long id) {
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
        String lastMessage = chatMessageRepo
                .findTopBySenderIdAndReceiverIdOrderBySentAtDesc(adminId, user.getId())
                .map(ChatMessage::getText)
                .orElse(null);

        if (lastMessage == null || lastMessage.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Envie uma mensagem antes de rejeitar temporariamente");
        }

        user.setStatus(UserStatus.TEMPORARILY_REJECTED);
        user.setRejectionReason(lastMessage);
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

                user.setStatus(UserStatus.REJECTED);
                user.setRejectionReason(ADMIN_DELETED_REASON);
                repo.save(user);

                emailService.sendAccountDeletedEmail(user);

                chatMessageRepo.deleteConversation(adminId, user.getId());
        }

    // ================= HISTORY =================

    @GetMapping("/users/{type}")
    public List<AdminUserResponse> getUsersHistory(
            @PathVariable UserType type,
            @RequestParam(required = false)
            UserStatus status
    ) {

        AuthContext.requireRole("ADMIN");

        List<User> users;

        if (status != null) {

            users =
                    repo.findByTypeAndStatus(
                            type,
                            status
                    );

        } else {

            users =
                    repo.findByTypeAndStatusIn(
                            type,
                            List.of(
                                    UserStatus.APPROVED,
                                    UserStatus.REJECTED
                            )
                    );
        }

        return users
                .stream()
                .map(AdminUserResponse::from)
                .toList();
    }

}