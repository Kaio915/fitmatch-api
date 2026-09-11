package fitmatch_api.service;

import fitmatch_api.model.User;
import java.text.Normalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    public void sendAccountDeletedEmail(User user) {
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

        String body = "Olá, " + greeting + "!\n\n"
                + "Sua conta no FitMatch foi EXCLUÍDA.\n\n"
                + "Você não conseguirá mais fazer login com essa conta.\n\n"
                + "Atenciosamente,\nEquipe FitMatch";

        boolean sent = send(user.getEmail(), "FitMatch - Conta excluída", body);
        if (!sent) {
            log.warn("Falha ao enviar e-mail de exclusão para {}.", user.getEmail());
        }
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
        sb.append("O que fazer agora:\n");
        sb.append("1. Acesse o FitMatch e na tela inicial, toque em \"Editar cadastro\".\n");
        sb.append("2. Selecione o tipo de usuário Aluno/Personal.\n");
        sb.append("3. Preencha os campos de email e senha (que foram colocados anteriormente no cadastro) e clique em \"Editar cadastro\".\n");
        sb.append("4. Corrija as informações indicadas pelo administrador.\n");
        sb.append("5. Envie o cadastro novamente.\n\n");
        sb.append("Assim que você reenviar, o seu cadastro voltará para a análise do administrador.\n\n");
        sb.append("Atenciosamente,\nEquipe FitMatch");
        return sb.toString();
    }
}
