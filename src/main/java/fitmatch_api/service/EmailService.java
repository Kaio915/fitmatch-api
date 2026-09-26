package fitmatch_api.service;

import fitmatch_api.model.User;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;
    private final boolean enabled;

    public EmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:no-reply@fitmatch.page}") String from,
            @Value("${app.mail.enabled:true}") boolean enabled
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
        this.enabled = enabled;
    }

    @Async("emailTaskExecutor")
    public void sendAdminMessageEmail(User user, String adminMessage) {
        if (!enabled) {
            log.info("Envio de e-mail desabilitado (app.mail.enabled=false). Mensagem não enviada.");
            return;
        }

        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Não foi possível enviar e-mail: usuário sem endereço de e-mail.");
            return;
        }

        boolean sent = send(user.getEmail(), "FitMatch - Atualização sobre o seu cadastro", buildBody(user, adminMessage));
        if (!sent) {
            log.warn("Falha ao enviar e-mail para {}.", user.getEmail());
        }
    }

    @Async("emailTaskExecutor")
    public void sendApprovalEmail(User user) {
        if (!enabled) {
            log.info("Envio de e-mail desabilitado (app.mail.enabled=false). Mensagem não enviada.");
            return;
        }
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Não foi possível enviar e-mail de aprovação: usuário sem endereço de e-mail.");
            return;
        }

        String name = user.getName() == null ? "" : user.getName().trim();
        String greeting = name.isEmpty() ? "usuário(a)" : name;

        String body = "Olá, " + greeting + "!\n\n"
                + "Seu cadastro no FitMatch foi APROVADO.\n\n"
                + "Você já pode acessar o aplicativo e começar a usar todos os recursos.\n\n"
                + "Atenciosamente,\nEquipe FitMatch";

        boolean sent = send(user.getEmail(), "FitMatch - Cadastro aprovado", body);
        if (!sent) {
            log.warn("Falha ao enviar e-mail de aprovação para {}.", user.getEmail());
        }
    }

    @Async("emailTaskExecutor")
    public void sendRejectionEmail(User user, String reason) {
        if (isEmailIssueReason(reason)) {
            log.info("E-mail de reprovação não enviado: o motivo indica problema com o e-mail informado pelo usuário.");
            return;
        }
        if (!enabled) {
            log.info("Envio de e-mail desabilitado (app.mail.enabled=false). Mensagem não enviada.");
            return;
        }
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Não foi possível enviar e-mail de reprovação: usuário sem endereço de e-mail.");
            return;
        }

        String name = user.getName() == null ? "" : user.getName().trim();
        String greeting = name.isEmpty() ? "usuário(a)" : name;

        StringBuilder sb = new StringBuilder();
        sb.append("Olá, ").append(greeting).append("!\n\n");
        sb.append("Seu cadastro no FitMatch foi REPROVADO.\n\n");
        if (reason != null && !reason.isBlank()) {
            sb.append("Motivo: ").append(reason.trim()).append("\n\n");
        }
        sb.append("Atenciosamente,\nEquipe FitMatch");

        boolean sent = send(user.getEmail(), "FitMatch - Cadastro reprovado", sb.toString());
        if (!sent) {
            log.warn("Falha ao enviar e-mail de reprovação para {}.", user.getEmail());
        }
    }

    @Async("emailTaskExecutor")
    public void sendAccountDeletedEmail(User user, String reason) {
        if (!enabled) {
            log.info("Envio de e-mail desabilitado (app.mail.enabled=false). Mensagem não enviada.");
            return;
        }
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Não foi possível enviar e-mail de exclusão: usuário sem endereço de e-mail.");
            return;
        }

        String name = user.getName() == null ? "" : user.getName().trim();
        String greeting = name.isEmpty() ? "usuário(a)" : name;

        String reasonText = (reason == null || reason.isBlank()) ? "" : reason.trim();

        String body = "Olá, " + greeting + "!\n\n"
                + "Sua conta no FitMatch foi EXCLUÍDA.\n\n"
                + (reasonText.isEmpty() ? "" : "Motivo: " + reasonText + "\n\n")
                + "Você não conseguirá mais fazer login com essa conta.\n\n"
                + "Atenciosamente,\nEquipe FitMatch";

        boolean sent = send(user.getEmail(), "FitMatch - Conta excluída", body);
        if (!sent) {
            log.warn("Falha ao enviar e-mail de exclusão para {}.", user.getEmail());
        }
    }

        @Async("emailTaskExecutor")
        public void sendScheduleRequestEmail(
            User trainer,
            String studentName,
            String planType,
            List<Map<String, String>> slots
        ) {
        if (!enabled) {
            log.info("Envio de e-mail desabilitado (app.mail.enabled=false). Mensagem não enviada.");
            return;
        }
        if (trainer == null || trainer.getEmail() == null || trainer.getEmail().isBlank()) {
            log.warn("Não foi possível enviar e-mail de solicitação: personal sem endereço de e-mail.");
            return;
        }

        String trainerName = trainer.getName() == null || trainer.getName().isBlank()
            ? "Personal"
            : trainer.getName().trim();
        String requester = studentName == null || studentName.isBlank() ? "Um aluno" : studentName.trim();
        String planLabel = "SEMANAL".equalsIgnoreCase(planType)
            ? "Plano semanal"
            : "MENSAL".equalsIgnoreCase(planType) ? "Plano mensal" : "Plano diário";

        StringBuilder body = new StringBuilder();
        body.append("Olá, ").append(trainerName).append("!\n\n")
            .append("Você recebeu uma nova solicitação de treino no FitMatch.\n\n")
            .append("Aluno: ").append(requester).append("\n")
            .append("Plano: ").append(planLabel).append("\n\n")
            .append("Horários solicitados:\n");
        List<Map<String, String>> emailSlots = slots == null
            ? new ArrayList<>()
            : new ArrayList<>(slots);
        if ("MENSAL".equalsIgnoreCase(planType) && emailSlots.size() > 1) {
            Map<String, String> firstSlot = emailSlots.get(0);
            Set<String> displayedPatterns = new HashSet<>();
            LocalDateTime firstAt = resolveSlotDateTime(firstSlot, LocalDateTime.now());
            LocalDateTime windowEnd = firstAt == null
                ? null
                : LocalDateTime.of(
                    firstAt.toLocalDate().plusMonths(1).minusDays(1),
                    LocalTime.MAX
                );
            Map<String, String> lastSlot = firstSlot;
            LocalDateTime lastAt = firstAt;

            appendScheduleSlot(body, firstSlot, true);
            displayedPatterns.add(slotPattern(firstSlot));
            for (int i = 1; i < emailSlots.size(); i++) {
                Map<String, String> slot = emailSlots.get(i);
                if (displayedPatterns.add(slotPattern(slot))) {
                    appendScheduleSlot(body, slot, false);
                }
                LocalDateTime slotAt = resolveSlotDateTime(slot, firstAt);
                if (slotAt != null && windowEnd != null) {
                    while (!slotAt.plusWeeks(1).isAfter(windowEnd)) {
                        slotAt = slotAt.plusWeeks(1);
                    }
                    if (lastAt == null || slotAt.isAfter(lastAt)) {
                        lastAt = slotAt;
                        lastSlot = slot;
                    }
                }
            }
            appendScheduleSlot(body, lastSlot, true, lastAt == null ? null : formatDateLabel(lastAt.toLocalDate()));
        } else {
            for (Map<String, String> slot : emailSlots) {
                appendScheduleSlot(body, slot, true);
            }
        }
        body.append("\nAcesse o FitMatch para analisar e responder à solicitação.\n\n")
            .append("Atenciosamente,\nEquipe FitMatch");

        boolean sent = send(
            trainer.getEmail(),
            "FitMatch - Nova solicitação de agendamento",
            body.toString()
        );
        if (!sent) log.warn("Falha ao enviar e-mail de solicitação para {}.", trainer.getEmail());
        }

    private void appendScheduleSlot(StringBuilder body, Map<String, String> slot, boolean includeDate) {
        appendScheduleSlot(body, slot, includeDate, null);
    }

    private void appendScheduleSlot(
        StringBuilder body,
        Map<String, String> slot,
        boolean includeDate,
        String dateOverride
    ) {
        String day = slot.getOrDefault("dayName", "").trim();
        String date = includeDate
            ? (dateOverride == null ? resolveDateLabel(slot) : dateOverride)
            : "";
        String time = slot.getOrDefault("time", "").trim();
        body.append("• ").append(day);
        if (!date.isEmpty()) body.append(" ").append(date);
        body.append(" às ").append(time).append("\n");
    }

    private LocalDateTime resolveSlotDateTime(Map<String, String> slot, LocalDateTime anchor) {
        String dateIso = slot.getOrDefault("dateIso", "").trim();
        String time = slot.getOrDefault("time", "").trim();
        int separator = time.indexOf(':');
        if (separator < 1) return null;

        try {
            int hour = Integer.parseInt(time.substring(0, separator));
            int minute = Integer.parseInt(time.substring(separator + 1, Math.min(separator + 3, time.length())));
            if (!dateIso.isEmpty()) {
                return LocalDateTime.of(LocalDate.parse(dateIso), LocalTime.of(hour, minute));
            }

            DayOfWeek weekday = weekdayFromPortuguese(slot.getOrDefault("dayName", ""));
            if (weekday == null || anchor == null) return null;
            LocalDateTime candidate = LocalDateTime.of(anchor.toLocalDate(), LocalTime.of(hour, minute));
            int days = weekday.getValue() - anchor.getDayOfWeek().getValue();
            if (days < 0) days += 7;
            candidate = candidate.plusDays(days);
            return candidate.isBefore(anchor) ? candidate.plusWeeks(1) : candidate;
        } catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
    }

    private DayOfWeek weekdayFromPortuguese(String dayName) {
        String normalized = Normalizer.normalize(dayName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
        return switch (normalized) {
            case "segunda" -> DayOfWeek.MONDAY;
            case "terca" -> DayOfWeek.TUESDAY;
            case "quarta" -> DayOfWeek.WEDNESDAY;
            case "quinta" -> DayOfWeek.THURSDAY;
            case "sexta" -> DayOfWeek.FRIDAY;
            case "sabado" -> DayOfWeek.SATURDAY;
            case "domingo" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private String formatDateLabel(LocalDate date) {
        return String.format(Locale.ROOT, "%02d/%02d", date.getDayOfMonth(), date.getMonthValue());
    }

    private String slotPattern(Map<String, String> slot) {
        return slot.getOrDefault("dayName", "").trim()
            + "|" + slot.getOrDefault("time", "").trim();
    }

    private String resolveDateLabel(Map<String, String> slot) {
        String date = slot.getOrDefault("dateLabel", "").trim();
        if (!date.isEmpty()) return date;

        String dateIso = slot.getOrDefault("dateIso", "").trim();
        if (!dateIso.isEmpty()) {
            try {
                LocalDate parsedDate = LocalDate.parse(dateIso);
                return String.format("%02d/%02d", parsedDate.getDayOfMonth(), parsedDate.getMonthValue());
            } catch (DateTimeParseException ignored) {
                return "";
            }
        }
        return "";
    }

    public boolean sendTestEmail(String to) {
        if (!enabled) {
            log.info("Envio de e-mail desabilitado (app.mail.enabled=false). Mensagem não enviada.");
            return false;
        }

        if (to == null || to.isBlank()) {
            log.warn("Não foi possível enviar e-mail de teste: destinatário vazio.");
            return false;
        }

        String body = "Olá!\n\n"
                + "Este é um e-mail de teste do FitMatch.\n\n"
                + "Se você recebeu esta mensagem, o envio de e-mails está funcionando corretamente.\n\n"
                + "Atenciosamente,\nEquipe FitMatch";

        return send(to, "FitMatch - Teste de envio de e-mail", body);
    }

    private boolean send(String to, String subject, String text) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Não foi possível enviar e-mail: JavaMailSender não configurado (defina spring.mail.*).");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
            log.info("E-mail enviado com sucesso para {}", to);
            return true;
        } catch (MailException e) {
            log.warn("Falha ao enviar e-mail para {}: {}", to, e.getMessage());
            return false;
        }
    }

    /**
     * Detecta motivos de rejeição relacionados a e-mail incorreto/errado.
     * Nesses casos não faz sentido enviar o e-mail de reprovação, pois o
     * endereço informado está errado. A normalização ignora acentos, hífens
     * e espaços para capturar variações como "e-mail", "e mail", "email".
     */
    private static boolean isEmailIssueReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        String compact = Normalizer.normalize(reason, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        return compact.contains("email");
    }

    private String buildBody(User user, String adminMessage) {
        String name = user.getName() == null ? "" : user.getName().trim();
        String greeting = name.isEmpty() ? "usuário(a)" : name;

        StringBuilder sb = new StringBuilder();
        sb.append("Olá, ").append(greeting).append("!\n\n");
        sb.append("O administrador do FitMatch enviou uma mensagem sobre o seu cadastro:\n\n");
        sb.append("\"").append(adminMessage == null ? "" : adminMessage.trim()).append("\"\n\n");
        sb.append("O que fazer agora:\n\n");
        sb.append("Acesse o FitMatch e na tela inicial, toque em seu tipo de usuário Aluno/Personal.\n\n");
        sb.append("Aperte em \"Editar Cadastro\".\n\n");
        sb.append("Preencha os campos de email e senha (que foram colocados anteriormente no cadastro) e clique em \"Editar cadastro\".\n\n");
        sb.append("Corrija as informações indicadas pelo administrador.\n\n");
        sb.append("Envie o cadastro novamente.\n\n");
        sb.append("Assim que você reenviar, o seu cadastro voltará para a análise do administrador.\n\n");
        sb.append("Atenciosamente,\nEquipe FitMatch");
        return sb.toString();
    }
}
