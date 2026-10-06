package fitmatch_api.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Responsável por enviar notificações push via Firebase Cloud Messaging.
 *
 * PRIVACIDADE: o conteúdo sensível (nome do remetente + trecho da mensagem)
 * viaja apenas no Data Payload. A "Notification" exibida pelo sistema (inclusive
 * na tela de bloqueio) é genérica, para não vazar dados do usuário.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Máximo de caracteres do trecho da mensagem enviado no data payload. */
    private static final int PREVIEW_MAX_LENGTH = 80;

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    public NotificationService(ObjectProvider<FirebaseMessaging> firebaseMessagingProvider) {
        this.firebaseMessagingProvider = firebaseMessagingProvider;
    }

    /** Indica se o FCM está configurado e pronto para envio. */
    public boolean isEnabled() {
        return firebaseMessagingProvider.getIfAvailable() != null;
    }

    /**
     * Envia notificação de nova mensagem de chat para o token do destinatário.
     *
     * @param fcmToken     token FCM do destinatário
     * @param senderId     id do remetente
     * @param receiverId   id do destinatário
     * @param senderName   nome do remetente (vai no data payload, não no título do sistema)
     * @param messageText  texto da mensagem (apenas um trecho vai no data payload)
     */
    @Async("notificationTaskExecutor")
    public void sendChatMessageNotification(String fcmToken, Long senderId, Long receiverId,
                                            String senderName, String messageText) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }

        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null) {
            log.debug("Firebase não configurado. Notificação push ignorada.");
            return;
        }

        String preview = buildPreview(messageText);

        // Notification genérica — segura para exibir na tela de bloqueio.
        Notification notification = Notification.builder()
                .setTitle("Nova mensagem")
                .setBody("Você recebeu uma nova mensagem no FitMatch")
                .build();

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification)
                .putData("type", "chat_message")
                .putData("senderId", String.valueOf(senderId))
                .putData("receiverId", String.valueOf(receiverId))
                .putData("senderName", senderName == null ? "" : senderName)
                .putData("messagePreview", preview)
                .build();

        try {
            String messageId = messaging.send(message);
            log.info("Notificação push enviada ({}). Destinatário: {}", messageId, receiverId);
        } catch (FirebaseMessagingException e) {
            log.warn("Falha ao enviar notificação push para {}: {}", receiverId, e.getMessage());
            // TODO(opcional): se e.getMessagingErrorCode() == UNREGISTERED,
            // limpar o fcmToken do usuário (token inválido/desinstalado).
        }
    }

    private String buildPreview(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.trim();
        if (clean.length() <= PREVIEW_MAX_LENGTH) {
            return clean;
        }
        return clean.substring(0, PREVIEW_MAX_LENGTH) + "…";
    }
}
