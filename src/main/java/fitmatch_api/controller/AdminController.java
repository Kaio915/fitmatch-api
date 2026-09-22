package fitmatch_api.controller;

import fitmatch_api.model.ChatMessage;
import fitmatch_api.model.Report;
import fitmatch_api.model.User;
import fitmatch_api.model.UserHistory;
import fitmatch_api.model.UserStatus;
import fitmatch_api.model.UserType;
import fitmatch_api.repository.ChatMessageRepository;
import fitmatch_api.repository.ReportRepository;
import fitmatch_api.repository.UserHistoryRepository;
import fitmatch_api.repository.UserRepository;
import fitmatch_api.security.AuthContext;
import fitmatch_api.service.EmailService;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

        private static final String ADMIN_DELETED_REASON = "Conta excluída pelo administrador";
        private static final String BAN_REASON = "Usuário banido da plataforma por violar as diretrizes";

    private final UserRepository repo;
    private final ChatMessageRepository chatMessageRepo;
    private final UserHistoryRepository historyRepo;
    private final ReportRepository reportRepo;
    private final EmailService emailService;
    private final Environment environment;

    public AdminController(UserRepository repo, ChatMessageRepository chatMessageRepo, UserHistoryRepository historyRepo, ReportRepository reportRepo, EmailService emailService, Environment environment) {
        this.repo = repo;
        this.chatMessageRepo = chatMessageRepo;
        this.historyRepo = historyRepo;
        this.reportRepo = reportRepo;
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

    // Copia a foto do usuário de forma defensiva: como o campo photo é LAZY,
    // um acesso fora de sessão pode lançar LazyInitializationException. Aqui
    // preferimos gravar null no histórico do que derrubar a requisição.
    private static byte[] userPhotoOrNull(User user) {
        try {
            return user.getPhoto();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ================= DTO =================

    public record AdminUserResponse(
            Long id,
            Long historyId,
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
            LocalDateTime recordedAt,
            String rejectionReason,
            boolean deleted,
            boolean banned,
            String currentStatus,
            boolean currentBanned
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

                    null,

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

                    null,

                    u.getRejectionReason(),

                    deletedByAdmin,

                    u.isBanned(),

                    u.getStatus() == null ? null : u.getStatus().name(),

                    u.isBanned()
            );
        }

        public static AdminUserResponse from(UserHistory h) {
            return from(h, null, null, false);
        }

        public static AdminUserResponse from(UserHistory h, byte[] fallbackPhoto, String currentStatus, boolean currentBanned) {
            byte[] photo = h.getPhoto();
            if ((photo == null || photo.length == 0) && fallbackPhoto != null && fallbackPhoto.length > 0) {
                photo = fallbackPhoto;
            }
            return new AdminUserResponse(
                    // Mantém o id do USUÁRIO (e não o id do registro do histórico)
                    // para que as ações do frontend (ex.: DELETE /admin/users/{id})
                    // continuem funcionando como antes.
                    h.getUserId(),
                    h.getId(),
                    h.getName(),
                    h.getEmail(),
                    h.getType() == null
                            ? null
                            : h.getType().name().toLowerCase(),
                    h.getStatus(),
                    maskCpf(h.getCpf()),
                    photoToBase64(photo),
                    h.getObjetivos(),
                    h.getNivel(),
                    h.getCref(),
                    h.getCidade(),
                    h.getEspecialidade(),
                    h.getExperiencia(),
                    h.getValorHora(),
                    h.getBio(),
                    h.getCreatedAt(),
                    h.getRecordedAt(),
                    h.getRejectionReason(),
                    h.isDeleted(),
                    h.isBanned(),
                    currentStatus,
                    currentBanned
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
        recordHistory(user, "APPROVED", null, false);

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

        // Captura a última mensagem enviada pelo admin para exibi-la no chat
        // quando o mesmo email se cadastrar novamente. A conversa é PRESERVADA
        // para que o histórico continue consultável (chat somente leitura); o
        // novo atendimento é separado pelo filtro "since" (createdAt do usuário).
        String lastAdminMessage = chatMessageRepo
                .findTopBySenderIdAndReceiverIdOrderBySentAtDesc(adminId, user.getId())
                .map(ChatMessage::getText)
                .orElse(null);

        recordHistory(user, "REJECTED", lastAdminMessage, false);

        emailService.sendRejectionEmail(user, reason);
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

        // Exclusão definitiva do usuário (usada para remover rejeitados/excluídos
        // do histórico). Caso o id não exista (usuário de teste órfão), apenas
        // limpa os resíduos de histórico e conversa que apontam para esse id.
        // O histórico é apenas OCULTADO (hidden = true), preservando o motivo da
        // rejeição/exclusão para exibir no chat em um novo cadastro do mesmo email.
        @DeleteMapping("/users/{id}")
        @Transactional
        public void deleteUser(@PathVariable Long id) {

                AuthContext.requireRole("ADMIN");

                Long adminId = AuthContext.requirePrincipal().userId();

                repo.findById(id).ifPresent(user -> {
                        if (user.getType() == UserType.admin) {
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                                "Não é permitido excluir conta de admin");
                        }
                        repo.delete(user);
                });

                // Em vez de apagar as tentativas do histórico, marca como "oculto"
                // (hidden = true) para preservar o motivo da rejeição/exclusão e
                // exibi-lo no chat caso o mesmo email/cpf se cadastre novamente.
                List<UserHistory> histories = historyRepo.findByUserId(id);
                for (UserHistory h : histories) {
                        h.setHidden(true);
                }
                historyRepo.saveAll(histories);

                // Remove as denúncias desse usuário.
                reportRepo.deleteByReportedUserId(id);

                // Remove o histórico de conversa entre o admin e o usuário.
                chatMessageRepo.deleteConversation(adminId, id);
        }

        // Oculta APENAS um registro do histórico (uma única tentativa de
        // cadastro), sem afetar os demais registros do mesmo usuário nem a
        // conta em si. O registro permanece no banco para preservar o motivo
        // da rejeição/exclusão exibido no chat.
        @DeleteMapping("/history-entry/{historyId}")
        @Transactional
        public void deleteHistoryEntry(@PathVariable Long historyId) {

                AuthContext.requireRole("ADMIN");

                UserHistory history = historyRepo.findById(historyId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Registro de histórico não encontrado"
                                ));

                // Em vez de apagar, marca como "oculto" (hidden = true) para
                // preservar o motivo da rejeição/exclusão exibido no chat quando
                // o mesmo email/cpf se cadastra novamente.
                history.setHidden(true);
                historyRepo.save(history);
        }

        // Oculta TODOS os registros de histórico de um usuário para um TIPO
        // específico (aluno OU personal). Não apaga o outro tipo, não exclui a
        // conta e não apaga as conversas — apenas limpa as tentativas daquele
        // tipo, mantendo o histórico do outro tipo separado. Os registros são
        // ocultados (hidden = true) para preservar o motivo da rejeição/exclusão.
        @DeleteMapping("/users/{id}/history")
        @Transactional
        public void deleteUserHistoryByType(
                @PathVariable Long id,
                @RequestParam UserType type
        ) {

                AuthContext.requireRole("ADMIN");

                // Em vez de apagar, marca como "oculto" (hidden = true) para
                // preservar os motivos de rejeição/exclusão exibidos no chat quando
                // o mesmo email/cpf se cadastra novamente.
                List<UserHistory> histories = historyRepo.findByUserIdAndType(id, type);
                for (UserHistory h : histories) {
                        h.setHidden(true);
                }
                historyRepo.saveAll(histories);
        }

        // Exclui a CONTA de um usuário aprovado (soft delete): ele deixa de
        // conseguir fazer login, mas o registro permanece no histórico marcado
        // como "excluído" — e só então pode ser limpo pelo "limpar histórico".
        @PutMapping("/users/{id}/exclude")
        @Transactional
        public void excludeAccount(
                @PathVariable Long id,
                @RequestBody(required = false) Map<String, String> body
        ) {

                AuthContext.requireRole("ADMIN");

                User user = repo.findById(id).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

                if (user.getType() == UserType.admin) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "Não é permitido excluir conta de admin");
                }

                // Motivo específico informado pelo admin (com fallback genérico).
                String reason = (body == null
                                || body.get("reason") == null
                                || body.get("reason").isBlank())
                        ? ADMIN_DELETED_REASON
                        : body.get("reason").trim();

                // Bloqueia o login: status REJECTED com o motivo de exclusão pelo admin.
                user.setStatus(UserStatus.REJECTED);
                user.setRejectionReason(ADMIN_DELETED_REASON);
                repo.save(user);

                // Marca o histórico aprovado como "excluído" (deleted = true) e
                // guarda o motivo específico da exclusão, para exibir no próximo
                // cadastro do mesmo email/cpf.
                List<UserHistory> approved = historyRepo.findActiveApprovedByUserId(id);
                for (UserHistory h : approved) {
                        h.setDeleted(true);
                        h.setDeletedAt(LocalDateTime.now());
                        h.setRejectionReason(reason);
                        historyRepo.save(h);
                }

                // Notifica o usuário aprovado de que a conta dele foi desativada/excluída,
                // informando o motivo específico.
                emailService.sendAccountDeletedEmail(user, reason);

                // A conversa é preservada para que o histórico continue consultável
                // (chat somente leitura a partir da tela de histórico do admin).

                // Remove as denúncias desse usuário (deixa de aparecer na lista de reportados).
                reportRepo.deleteByReportedUserId(id);
        }

        // ================= BAN =================

        // Bane o usuário e, dependendo do estado atual, rejeita o cadastro (se
        // pendente) ou exclui a conta (se aprovada). O banimento impede novos
        // cadastros e logins com o mesmo email/cpf.
        @PutMapping("/ban/{id}")
        @Transactional
        public void banUser(
                @PathVariable Long id,
                @RequestBody(required = false) Map<String, String> body
        ) {

                AuthContext.requireRole("ADMIN");
                Long adminId = AuthContext.requirePrincipal().userId();

                User user = repo.findById(id).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

                if (user.getType() == UserType.admin) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "Não é permitido banir um administrador");
                }

                String reason = (body == null
                                || body.get("reason") == null
                                || body.get("reason").isBlank())
                        ? BAN_REASON
                        : body.get("reason").trim();

                boolean wasApproved = user.getStatus() == UserStatus.APPROVED;

                user.setBanned(true);
                user.setStatus(UserStatus.REJECTED);
                user.setRejectionReason(reason);
                repo.save(user);

                // Registra o banimento no histórico (status "REJECTED" + banned).
                recordHistory(user, "REJECTED", null, true);

                if (wasApproved) {
                        // Exclusão automática da conta aprovada.
                        List<UserHistory> approved = historyRepo.findActiveApprovedByUserId(id);
                        for (UserHistory h : approved) {
                                h.setDeleted(true);
                                h.setRejectionReason(reason);
                                historyRepo.save(h);
                        }
                        emailService.sendAccountDeletedEmail(user, reason);
                } else {
                        emailService.sendRejectionEmail(user, reason);
                        chatMessageRepo.deleteConversation(adminId, id);
                }
        }

        // Desfaz o banimento: o usuário volta a poder fazer login/cadastro.
        // O status permanece REJECTED para que ele possa se cadastrar novamente.
        @PutMapping("/unban/{id}")
        @Transactional
        public void unbanUser(@PathVariable Long id) {

                AuthContext.requireRole("ADMIN");

                User user = repo.findById(id).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

                user.setBanned(false);
                repo.save(user);

                List<UserHistory> bannedRecords = historyRepo.findByUserIdAndBanned(id, true);
                for (UserHistory h : bannedRecords) {
                        h.setBanned(false);
                        historyRepo.save(h);
                }
        }

    // ================= REPORTS (USUÁRIOS REPORTADOS) =================

    // Contagem de usuários reportados ainda não visualizados pelo admin.
    @GetMapping("/reports/count")
    public Map<String, Object> reportedUsersCount() {
        AuthContext.requireRole("ADMIN");
        Map<String, Object> m = new HashMap<>();
        m.put("count", reportRepo.countDistinctReportedUsersUnseen());
        return m;
    }

    // Lista consolidada de usuários reportados. Cada item representa um usuário
    // (com os mesmos campos do histórico) acrescido de "new" (denúncia não vista),
    // "reportCount", "lastReportAt" e "lastReportReason". Ao abrir a lista, todas
    // as denúncias são marcadas como vistas (a contagem zera, mas os registros
    // permanecem até o usuário ser excluído).
    @GetMapping("/reports")
    @Transactional
    public List<Map<String, Object>> getReportedUsers() {
        AuthContext.requireRole("ADMIN");

        List<Report> reports = reportRepo.findAllByOrderByCreatedAtDesc();

        Map<Long, List<Report>> grouped = new LinkedHashMap<>();
        for (Report r : reports) {
            grouped.computeIfAbsent(r.getReportedUserId(), k -> new ArrayList<>()).add(r);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<Report>> entry : grouped.entrySet()) {
            Long reportedUserId = entry.getKey();
            List<Report> userReports = entry.getValue();

            User user = repo.findById(reportedUserId).orElse(null);
            if (user == null) {
                // Usuário já removido: descarta os registros órfãos.
                reportRepo.deleteByReportedUserId(reportedUserId);
                continue;
            }

            boolean isNew = userReports.stream().anyMatch(r -> !r.isSeen());
            Report latest = userReports.get(0); // ordenado por createdAt desc

            Map<String, Object> m = adminUserToMap(user);
            m.put("new", isNew);
            m.put("reportCount", userReports.size());
            m.put("lastReportAt", latest.getCreatedAt() == null ? null : latest.getCreatedAt().toString());
            m.put("lastReportReason", latest.getReason());
            m.put("lastReportDetails", latest.getDetails());
            result.add(m);
        }

        // Zera a contagem de "novos" após a lista ser visualizada.
        reportRepo.markAllSeen();

        return result;
    }

    // ================= HISTORY =================

    @GetMapping("/users/{type}")
    @Transactional(readOnly = true)
    public List<AdminUserResponse> getUsersHistory(
            @PathVariable UserType type,
            @RequestParam(required = false)
            String status
    ) {

        AuthContext.requireRole("ADMIN");

        List<UserHistory> history;

        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            if ("BANNED".equals(normalized)) {
                history = historyRepo.findByTypeAndBannedAndHiddenOrderByRecordedAtDesc(type, true, false);
            } else {
                history = historyRepo.findByTypeAndStatusAndHiddenOrderByRecordedAtDesc(
                        type,
                        normalized,
                        false
                );
            }
        } else {
            history = historyRepo.findByTypeAndHiddenOrderByRecordedAtDesc(type, false);
        }

        return history
                .stream()
                .map(h -> AdminUserResponse.from(h, fallbackPhoto(h.getUserId()), currentStatusOf(h.getUserId()), currentBannedOf(h.getUserId())))
                .toList();
    }

    // Quando um registro de histórico foi gravado sem foto (ex.: backfill de
    // usuários antigos ou tentativa anterior sem foto), reutiliza a foto atual
    // do usuário para que o admin nunca veja o histórico sem imagem.
    private byte[] fallbackPhoto(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return repo.findById(userId).map(User::getPhoto).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Status ATUAL do usuário (não o status do registro de histórico). Usado pelo
    // frontend para bloquear o botão "Banir" no histórico quando o usuário voltou
    // a ficar PENDENTE/em análise — nesse caso o banimento deve ser feito pelo chat.
    private String currentStatusOf(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return repo.findById(userId)
                    .map(u -> u.getStatus() == null ? null : u.getStatus().name())
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Indica se o usuário está ATUALMENTE banido (não o registro de histórico).
    // Usado pelo frontend para desabilitar o botão "Banir" em registros antigos
    // (ex.: conta excluída) quando o usuário já foi banido em outra tentativa.
    private boolean currentBannedOf(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return repo.findById(userId).map(User::isBanned).orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ================= PREVIOUS REJECTION =================

    // Retorna a rejeição mais recente para um email (motivo + última mensagem
    // enviada pelo admin), usada no chat para exibir o histórico da tentativa
    // anterior quando o usuário se cadastra novamente com o mesmo email.
    //
    // Exibe a rejeição sempre que houver alguma no histórico deste email,
    // independentemente de ter sido seguida por uma aprovação/exclusão. Assim o
    // admin vê o motivo da rejeição E o motivo da exclusão (via previous-exclusion)
    // quando ambos aconteceram.
    @GetMapping("/previous-rejection")
    public Map<String, Object> getPreviousRejection(@RequestParam String email) {

        AuthContext.requireRole("ADMIN");

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email é obrigatório");
        }

        // Todas as rejeições do email (da mais recente para a mais antiga).
        List<UserHistory> rejections = historyRepo
                .findByEmailAndStatusOrderByRecordedAtDesc(normalizedEmail, "REJECTED");

        Map<String, Object> m = new HashMap<>();
        if (rejections.isEmpty()) {
            m.put("found", false);
            return m;
        }

        UserHistory latest = rejections.get(0);

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = rejections.size() - 1; i >= 0; i--) {
            UserHistory h = rejections.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("rejectionReason", h.getRejectionReason());
            item.put("lastAdminMessage", h.getLastAdminMessage());
            item.put("recordedAt", h.getRecordedAt() == null ? null : h.getRecordedAt().toString());
            items.add(item);
        }

        m.put("found", true);
        m.put("email", latest.getEmail());
        m.put("status", "REJECTED");
        m.put("rejectionReason", latest.getRejectionReason());
        m.put("lastAdminMessage", latest.getLastAdminMessage());
        m.put("recordedAt", latest.getRecordedAt() == null ? null : latest.getRecordedAt().toString());
        m.put("rejections", items);
        return m;
    }

    // Retorna TODAS as exclusões de conta de um email (da mais recente para a
    // mais antiga), usada no chat para avisar o admin quando o mesmo usuário
    // (email/cpf) cadastra-se novamente após ter a conta excluída. Exibe todos
    // os motivos, e não apenas o da última exclusão.
    //
    // Exibe a exclusão sempre que houver alguma no histórico deste email,
    // independentemente de ter havido uma rejeição depois. Assim o admin vê o
    // motivo da rejeição (via previous-rejection) E o motivo da exclusão quando
    // ambos aconteceram.
    @GetMapping("/previous-exclusion")
    public Map<String, Object> getPreviousExclusion(@RequestParam String email) {

        AuthContext.requireRole("ADMIN");

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email é obrigatório");
        }

        // Todas as exclusões de conta do email (da mais recente para a mais antiga).
        List<UserHistory> exclusions = historyRepo
                .findExclusionsByEmail(normalizedEmail);

        Map<String, Object> m = new HashMap<>();
        if (exclusions.isEmpty()) {
            m.put("found", false);
            return m;
        }

        UserHistory latest = exclusions.get(0);

        m.put("found", true);
        m.put("email", latest.getEmail());

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = exclusions.size() - 1; i >= 0; i--) {
            UserHistory h = exclusions.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("exclusionReason", h.getRejectionReason());
            item.put("excludedAt", h.getDeletedAt() == null
                    ? (h.getRecordedAt() == null ? null : h.getRecordedAt().toString())
                    : h.getDeletedAt().toString());
            item.put("recordedAt", h.getRecordedAt() == null ? null : h.getRecordedAt().toString());
            items.add(item);
        }
        m.put("exclusions", items);
        return m;
    }

    // Limpa o histórico de um tipo de usuário, podendo filtrar por status
    // (REJECTED/DELETED) ou limpar tudo EXCETO os aprovados. Aprovados NÃO
    // podem ter o histórico limpo: para removê-los, use a exclusão de conta.
    //
    // As conversas NÃO são apagadas aqui: o "limpar histórico" serve apenas
    // para deixar a tela limpa, preservando o chat para que, caso o usuário
    // se cadastre novamente, o admin ainda veja as mensagens antigas.
    @DeleteMapping("/history/{type}")
    @Transactional
    public void clearHistory(
            @PathVariable UserType type,
            @RequestParam(required = false) String status
    ) {

        AuthContext.requireRole("ADMIN");

        String filter = (status == null || status.isBlank())
                ? "ALL"
                : status.trim().toUpperCase();

        if ("APPROVED".equals(filter)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Não é permitido limpar o histórico de usuários aprovados. " +
                            "Use a exclusão de conta para remover um usuário aprovado."
            );
        }

        List<UserHistory> targets;
        switch (filter) {
            case "REJECTED" -> targets = historyRepo.findByTypeAndStatusOrderByRecordedAtDesc(type, "REJECTED");
            case "DELETED" -> targets = historyRepo.findExcluded(type);
            case "BANNED" -> targets = historyRepo.findByTypeAndBannedOrderByRecordedAtDesc(type, true);
            // "ALL" (limpar tudo) não pode remover aprovados ativos.
            default -> targets = historyRepo.findExcludingActiveApproved(type);
        }

        // Em vez de apagar, marca como "oculto" (hidden = true). Assim a tela de
        // histórico fica limpa, mas os motivos de rejeição/exclusão continuam
        // disponíveis para exibir no chat quando o mesmo email se cadastrar de novo.
        for (UserHistory h : targets) {
            h.setHidden(true);
        }
        historyRepo.saveAll(targets);
    }

    // Converte um User em um mapa com os mesmos campos usados no histórico de
    // usuários (para reaproveitar o formato no frontend da lista de reportados).
    private static Map<String, Object> adminUserToMap(User u) {
        String reason = u.getRejectionReason();
        boolean deletedByAdmin = reason != null
                && reason.trim().equalsIgnoreCase(ADMIN_DELETED_REASON);
        String statusLabel = deletedByAdmin
                ? "DELETED"
                : (u.getStatus() == null ? null : u.getStatus().name());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("type", u.getType() == null ? null : u.getType().name().toLowerCase());
        m.put("status", statusLabel);
        m.put("cpf", maskCpf(u.getCpf()));
        m.put("photoBase64", photoToBase64(u.getPhoto()));
        m.put("objetivos", u.getObjetivos());
        m.put("nivel", u.getNivel());
        m.put("cref", u.getCref());
        m.put("cidade", u.getCidade());
        m.put("especialidade", u.getEspecialidade());
        m.put("experiencia", u.getExperiencia());
        m.put("valorHora", u.getValorHora());
        m.put("bio", u.getBio());
        m.put("createdAt", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
        m.put("recordedAt", null);
        m.put("rejectionReason", u.getRejectionReason());
        m.put("deleted", deletedByAdmin);
        m.put("banned", u.isBanned());
        return m;
    }

    // Registra no histórico cada tentativa terminal (aprovado/rejeitado/excluído),
    // preservando um snapshot mesmo quando a linha de "users" é reutilizada em um
    // novo cadastro.
    private void recordHistory(User user, String status, String lastAdminMessage, boolean banned) {
        UserHistory h = new UserHistory();
        h.setUserId(user.getId());
        h.setName(user.getName());
        h.setEmail(user.getEmail());
        h.setCpf(user.getCpf());
        h.setType(user.getType());
        h.setStatus(status);
        h.setRejectionReason(user.getRejectionReason());
        h.setLastAdminMessage(lastAdminMessage);
        h.setBanned(banned);
        h.setPhoto(userPhotoOrNull(user));
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