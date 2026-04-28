package fitmatch_api.controller;

import fitmatch_api.model.ChatMessage;
import fitmatch_api.model.StudentRequest;
import fitmatch_api.model.User;
import fitmatch_api.model.UserType;
import fitmatch_api.repository.BlockedStudentRepository;
import fitmatch_api.repository.ChatMessageRepository;
import fitmatch_api.repository.StudentRequestRepository;
import fitmatch_api.repository.UserRepository;
import fitmatch_api.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Pattern REQUEST_MARKER_PATTERN = Pattern.compile("\\[\\[REQ:(\\d+)\\]\\]");
    private static final Pattern SLOT_PATTERN = Pattern.compile(
            "(segunda|terca|terça|quarta|quinta|sexta|sabado|sábado|domingo)\\s*(as|às)\\s*(\\d{1,2}:\\d{2})",
            Pattern.CASE_INSENSITIVE
    );
        private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[^{}]*}");
        private static final Pattern JSON_DAY_NAME_PATTERN = Pattern.compile("\"dayName\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern JSON_TIME_PATTERN = Pattern.compile("\"time\"\\s*:\\s*\"([^\"]+)\"");

    private final ChatMessageRepository repo;
    private final BlockedStudentRepository blockedStudentRepo;
    private final UserRepository userRepo;
    private final StudentRequestRepository requestRepo;

    public ChatController(
            ChatMessageRepository repo,
            BlockedStudentRepository blockedStudentRepo,
            UserRepository userRepo,
            StudentRequestRepository requestRepo
    ) {
        this.repo = repo;
        this.blockedStudentRepo = blockedStudentRepo;
        this.userRepo = userRepo;
        this.requestRepo = requestRepo;
    }

        private boolean hasActiveRequest(Long studentId, Long trainerId) {
        boolean hasPending = !requestRepo
            .findByStudentIdAndTrainerIdAndStatusOrderByCreatedAtDesc(studentId, trainerId, "PENDING")
            .isEmpty();
        boolean hasApproved = !requestRepo
            .findByStudentIdAndTrainerIdAndStatusOrderByCreatedAtDesc(studentId, trainerId, "APPROVED")
            .isEmpty();

        return hasPending || hasApproved;
        }

        private boolean canSendMessageBetweenUsers(Long senderId, Long receiverId) {
        User sender = userRepo.findById(senderId).orElse(null);
        User receiver = userRepo.findById(receiverId).orElse(null);

        if (sender == null || receiver == null) {
            return true;
        }

        boolean senderStudentReceiverTrainer = sender.getType() == UserType.aluno
            && receiver.getType() == UserType.personal;
        boolean senderTrainerReceiverStudent = sender.getType() == UserType.personal
            && receiver.getType() == UserType.aluno;

        if (!senderStudentReceiverTrainer && !senderTrainerReceiverStudent) {
            return true;
        }

        Long studentId = senderStudentReceiverTrainer ? senderId : receiverId;
        Long trainerId = senderStudentReceiverTrainer ? receiverId : senderId;

        return hasActiveRequest(studentId, trainerId);
    }

    /** Envia uma mensagem */
    @PostMapping("/send")
    public ChatMessage sendMessage(@RequestBody MessageDto dto) {
        if (dto.senderId() == null || dto.receiverId() == null
                || dto.text() == null || dto.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "senderId, receiverId e text são obrigatórios");
        }

        AuthContext.requireSelfOrAdmin(dto.senderId());

        boolean blocked = blockedStudentRepo.existsByTrainerIdAndStudentId(dto.senderId(), dto.receiverId())
            || blockedStudentRepo.existsByTrainerIdAndStudentId(dto.receiverId(), dto.senderId());
        if (blocked) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Conversa bloqueada entre este aluno e personal");
        }

        if (!canSendMessageBetweenUsers(dto.senderId(), dto.receiverId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Chat disponível somente para leitura. Para enviar mensagens, é necessário ter uma solicitação ativa entre aluno e personal");
        }

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(dto.senderId());
        msg.setReceiverId(dto.receiverId());
        msg.setText(dto.text());
        return repo.save(msg);
    }

    /** Retorna todas as mensagens entre dois usuários, ordenadas por data */
    @GetMapping("/conversation")
    public List<ChatMessage> getConversation(
            @RequestParam Long userId1,
            @RequestParam Long userId2,
            @RequestParam(required = false) Long requestId) {
        AuthContext.requireSelfOrAdminFromAny(userId1, userId2);
        List<ChatMessage> conversation = repo.findConversation(userId1, userId2);
        if (requestId == null) {
            return conversation;
        }

        StudentRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Solicitação não encontrada para filtrar conversa"
                ));

        Long studentId = request.getStudentId();
        Long trainerId = request.getTrainerId();
        if (studentId == null || trainerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitação sem vínculo aluno/personal");
        }

        boolean pairMatchesRequest =
                (userId1.equals(studentId) && userId2.equals(trainerId)) ||
                (userId1.equals(trainerId) && userId2.equals(studentId));

        if (!pairMatchesRequest) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "requestId informado não pertence à conversa deste par de usuários"
            );
        }

        LocalDateTime startAt = request.getCreatedAt();
        LocalDateTime lockAt = resolveNextRequestCreatedAt(studentId, trainerId, startAt, requestId);
        Set<String> requestSlots = extractRequestSlots(request);

        return conversation.stream()
            .filter(message -> belongsToRequestWindow(message, requestId, startAt, lockAt, requestSlots))
                .collect(Collectors.toList());
    }

    private LocalDateTime resolveNextRequestCreatedAt(
            Long studentId,
            Long trainerId,
            LocalDateTime currentCreatedAt,
            Long currentRequestId
    ) {
        if (currentCreatedAt == null) {
            return null;
        }

        return requestRepo.findByStudentIdAndTrainerIdOrderByCreatedAtDesc(studentId, trainerId)
                .stream()
                .filter(req -> req.getId() != null && !req.getId().equals(currentRequestId))
                .filter(req -> {
                    LocalDateTime createdAt = req.getCreatedAt();
                    if (createdAt == null) return false;

                    if (createdAt.isAfter(currentCreatedAt)) {
                        return true;
                    }

                    if (createdAt.isEqual(currentCreatedAt) && currentRequestId != null && req.getId() != null) {
                        // Empate de timestamp: usa ID como desempate do próximo ciclo.
                        return req.getId() > currentRequestId;
                    }

                    return false;
                })
                .map(StudentRequest::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean belongsToRequestWindow(
            ChatMessage message,
            Long requestId,
            LocalDateTime startAt,
            LocalDateTime lockAt,
            Set<String> requestSlots
    ) {
        Long markerId = extractRequestMarkerId(message.getText());

        // Marcador explícito de outro ciclo: nunca entra neste chat.
        if (markerId != null && !markerId.equals(requestId)) {
            return false;
        }

        // Marcador explícito do ciclo atual sempre aparece (ex.: mensagem de bloqueio pós-lock).
        if (markerId != null) {
            return true;
        }

        LocalDateTime sentAt = message.getSentAt();
        if (sentAt == null) {
            return startAt == null && lockAt == null;
        }

        if (startAt != null && sentAt.isBefore(startAt)) {
            return false;
        }

        // lockAt exclusivo: mensagens exatamente no início do próximo ciclo não pertencem ao chat atual.
        if (lockAt != null && !sentAt.isBefore(lockAt)) {
            return false;
        }

        // Mensagens iniciais legadas (sem marcador) só pertencem ao chat
        // quando o slot bate com a solicitação atual.
        if (isInitialRequestMessage(message.getText())
                && !messageMatchesRequestSlots(message.getText(), requestSlots)) {
            return false;
        }

        return true;
    }

    private boolean isInitialRequestMessage(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return normalize(text).contains("gostaria de solicitar");
    }

    private boolean messageMatchesRequestSlots(String text, Set<String> requestSlots) {
        if (requestSlots.isEmpty()) {
            return true;
        }
        Set<String> messageSlots = extractMessageSlots(text);
        if (messageSlots.isEmpty()) {
            return false;
        }
        return messageSlots.equals(requestSlots);
    }

    private Set<String> extractRequestSlots(StudentRequest request) {
        Set<String> slots = new HashSet<>();

        if (request == null) {
            return slots;
        }

        String daysJson = request.getDaysJson();
        if (daysJson != null && !daysJson.isBlank()) {
            Matcher objectMatcher = JSON_OBJECT_PATTERN.matcher(daysJson);
            while (objectMatcher.find()) {
                String objectText = objectMatcher.group();
                String dayName = extractJsonValue(objectText, JSON_DAY_NAME_PATTERN);
                String time = extractJsonValue(objectText, JSON_TIME_PATTERN);
                addSlot(slots, dayName, time);
            }
        }

        if (slots.isEmpty()) {
            addSlot(slots, request.getDayName(), request.getTime());
        }

        return slots;
    }

    private String extractJsonValue(String text, Pattern pattern) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1) == null ? "" : matcher.group(1);
    }

    private Set<String> extractMessageSlots(String text) {
        Set<String> slots = new HashSet<>();
        if (text == null || text.isBlank()) {
            return slots;
        }

        Matcher matcher = SLOT_PATTERN.matcher(normalize(text));
        while (matcher.find()) {
            addSlot(slots, matcher.group(1), matcher.group(3));
        }
        return slots;
    }

    private void addSlot(Set<String> slots, String dayName, String time) {
        String normalizedDay = normalize(dayName);
        String normalizedTime = normalize(time);
        if (normalizedDay.isBlank() || normalizedTime.isBlank()) {
            return;
        }
        slots.add(normalizedDay + "|" + normalizedTime);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .toLowerCase(Locale.ROOT)
                .replace('á', 'a')
                .replace('à', 'a')
                .replace('â', 'a')
                .replace('ã', 'a')
                .replace('é', 'e')
                .replace('ê', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ô', 'o')
                .replace('õ', 'o')
                .replace('ú', 'u')
                .replace('ç', 'c')
                .replaceAll("\\\\s+", " ")
                .trim();
    }

    private Long extractRequestMarkerId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = REQUEST_MARKER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record MessageDto(Long senderId, Long receiverId, String text) {}
}
