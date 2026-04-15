package fitmatch_api.controller;

import fitmatch_api.model.BlockedStudent;
import fitmatch_api.model.ChatMessage;
import fitmatch_api.model.StudentRequest;
import fitmatch_api.model.StudentTrainerConnection;
import fitmatch_api.repository.BlockedStudentRepository;
import fitmatch_api.repository.ChatMessageRepository;
import fitmatch_api.repository.StudentRequestRepository;
import fitmatch_api.repository.StudentTrainerConnectionRepository;
import fitmatch_api.repository.TrainerSlotRepository;
import fitmatch_api.repository.UserRepository;
import fitmatch_api.service.BlockedStudentService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/connections")
@CrossOrigin(origins = "*")
public class ConnectionController {

    private final StudentTrainerConnectionRepository repo;
    private final UserRepository userRepo;
    private final BlockedStudentRepository blockedStudentRepo;
    private final StudentRequestRepository requestRepo;
        private final TrainerSlotRepository slotRepo;
    private final BlockedStudentService blockedStudentService;
    private final ChatMessageRepository chatMessageRepo;
        private static final Pattern DAY_TIME_PATTERN_DOUBLE = Pattern.compile(
            "\\\"dayName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"time\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
        );
        private static final Pattern DAY_TIME_PATTERN_DOUBLE_REVERSED = Pattern.compile(
            "\\\"time\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"dayName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
        );
        private static final Pattern DAY_TIME_PATTERN_SINGLE = Pattern.compile(
            "'dayName'\\s*:\\s*'([^']+)'\\s*,\\s*'time'\\s*:\\s*'([^']+)'"
        );
        private static final Pattern DAY_TIME_PATTERN_SINGLE_REVERSED = Pattern.compile(
            "'time'\\s*:\\s*'([^']+)'\\s*,\\s*'dayName'\\s*:\\s*'([^']+)'"
        );

    public ConnectionController(
            StudentTrainerConnectionRepository repo,
            UserRepository userRepo,
            BlockedStudentRepository blockedStudentRepo,
            StudentRequestRepository requestRepo,
            TrainerSlotRepository slotRepo,
            BlockedStudentService blockedStudentService,
            ChatMessageRepository chatMessageRepo
    ) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.blockedStudentRepo = blockedStudentRepo;
        this.requestRepo = requestRepo;
        this.slotRepo = slotRepo;
        this.blockedStudentService = blockedStudentService;
        this.chatMessageRepo = chatMessageRepo;
    }

    private List<Map<String, String>> parseSlotsFromJson(String rawJson) {
        List<Map<String, String>> slots = new ArrayList<>();

        Matcher matcher = DAY_TIME_PATTERN_DOUBLE.matcher(rawJson);
        while (matcher.find()) {
            String dayName = matcher.group(1).trim();
            String time = matcher.group(2).trim();
            if (!dayName.isEmpty() && !time.isEmpty()) {
                slots.add(Map.of("dayName", dayName, "time", time));
            }
        }

        Matcher reversedMatcher = DAY_TIME_PATTERN_DOUBLE_REVERSED.matcher(rawJson);
        while (reversedMatcher.find()) {
            String dayName = reversedMatcher.group(2).trim();
            String time = reversedMatcher.group(1).trim();
            if (!dayName.isEmpty() && !time.isEmpty()) {
                slots.add(Map.of("dayName", dayName, "time", time));
            }
        }

        Matcher singleMatcher = DAY_TIME_PATTERN_SINGLE.matcher(rawJson);
        while (singleMatcher.find()) {
            String dayName = singleMatcher.group(1).trim();
            String time = singleMatcher.group(2).trim();
            if (!dayName.isEmpty() && !time.isEmpty()) {
                slots.add(Map.of("dayName", dayName, "time", time));
            }
        }

        Matcher singleReversedMatcher = DAY_TIME_PATTERN_SINGLE_REVERSED.matcher(rawJson);
        while (singleReversedMatcher.find()) {
            String dayName = singleReversedMatcher.group(2).trim();
            String time = singleReversedMatcher.group(1).trim();
            if (!dayName.isEmpty() && !time.isEmpty()) {
                slots.add(Map.of("dayName", dayName, "time", time));
            }
        }

        return slots;
    }

    private List<Map<String, String>> extractSelectedSlots(StudentRequest req) {
        if (req == null) {
            return new ArrayList<>();
        }

        List<Map<String, String>> slots = new ArrayList<>();
        String rawDaysJson = req.getDaysJson();
        if (rawDaysJson != null && !rawDaysJson.isBlank()) {
            slots = parseSlotsFromJson(rawDaysJson);
        }

        if (slots.isEmpty()) {
            String dayName = req.getDayName() == null ? "" : req.getDayName();
            String time = req.getTime() == null ? "" : req.getTime();
            if (!dayName.isBlank() || !time.isBlank()) {
                slots.add(Map.of(
                        "dayName", dayName,
                        "time", time
                ));
            }
        }
        return slots;
    }

    private boolean shouldPersistRequestSlot(StudentRequest req) {
        if (req == null || req.getPlanType() == null) {
            return false;
        }
        return "SEMANAL".equalsIgnoreCase(req.getPlanType().trim());
    }

    private boolean slotIsUsedByAnotherApprovedRequestExcludingStudent(Long trainerId, Long blockedStudentId, String dayName, String time) {
        return requestRepo.findByTrainerIdAndStatus(trainerId, "APPROVED")
                .stream()
            .filter(this::shouldPersistRequestSlot)
                .filter(other -> other.getStudentId() != null && !other.getStudentId().equals(blockedStudentId))
                .anyMatch(other -> extractSelectedSlots(other)
                        .stream()
                        .anyMatch(slot -> dayName.equals(slot.getOrDefault("dayName", "").trim())
                                && time.equals(slot.getOrDefault("time", "").trim())));
    }

    private void releaseRequestSlotsForBlockedStudent(StudentRequest req, Long blockedStudentId) {
        List<Map<String, String>> selectedSlots = extractSelectedSlots(req);
        for (Map<String, String> slot : selectedSlots) {
            String dayName = slot.getOrDefault("dayName", "").trim();
            String time = slot.getOrDefault("time", "").trim();
            if (!dayName.isEmpty()
                    && !time.isEmpty()
                    && !slotIsUsedByAnotherApprovedRequestExcludingStudent(req.getTrainerId(), blockedStudentId, dayName, time)) {
                slotRepo.deleteByTrainerIdAndDayNameAndTimeAndState(
                        req.getTrainerId(),
                        dayName,
                        time,
                        "REQUEST"
                );
            }
        }
    }

    private void rebuildTrainerSlotsFromApprovedRequests(Long trainerId) {
        List<StudentRequest> approvedRequests = requestRepo.findByTrainerIdAndStatus(trainerId, "APPROVED");
        for (StudentRequest req : approvedRequests) {
            if (!shouldPersistRequestSlot(req)) {
                for (Map<String, String> slot : extractSelectedSlots(req)) {
                    String dayName = slot.getOrDefault("dayName", "").trim();
                    String time = slot.getOrDefault("time", "").trim();
                    if (dayName.isEmpty() || time.isEmpty()) {
                        continue;
                    }
                    slotRepo.deleteByTrainerIdAndDayNameAndTimeAndState(
                            trainerId,
                            dayName,
                            time,
                            "REQUEST"
                    );
                }
                continue;
            }
            for (Map<String, String> slot : extractSelectedSlots(req)) {
                String dayName = slot.getOrDefault("dayName", "").trim();
                String time = slot.getOrDefault("time", "").trim();
                if (dayName.isEmpty() || time.isEmpty()) {
                    continue;
                }

                slotRepo.findByTrainerIdAndDayNameAndTime(trainerId, dayName, time)
                        .orElseGet(() -> {
                            fitmatch_api.model.TrainerSlot blockedSlot = new fitmatch_api.model.TrainerSlot();
                            blockedSlot.setTrainerId(trainerId);
                            blockedSlot.setDayName(dayName);
                            blockedSlot.setTime(time);
                            blockedSlot.setState("REQUEST");
                            return slotRepo.save(blockedSlot);
                        });
            }
        }
    }

    private boolean hasTerminationMessageInCurrentCycle(Long trainerId, Long studentId, StudentRequest referenceReq) {
        if (referenceReq == null) {
            return chatMessageRepo.hasTerminationMessage(trainerId, studentId);
        }

        LocalDateTime cycleStartAt = referenceReq.getCreatedAt();
        if (cycleStartAt == null) {
            return chatMessageRepo.hasTerminationMessage(trainerId, studentId);
        }

        return chatMessageRepo.hasTerminationMessageSince(trainerId, studentId, cycleStartAt);
    }

    private String resolveTrainerName(StudentRequest req, Long trainerId) {
        if (req != null && req.getTrainerName() != null && !req.getTrainerName().isBlank()) {
            return req.getTrainerName();
        }
        return "Personal #" + trainerId;
    }

    private String resolveStudentName(StudentRequest req, Long studentId) {
        if (req != null && req.getStudentName() != null && !req.getStudentName().isBlank()) {
            return req.getStudentName();
        }
        return "Aluno #" + studentId;
    }

    private String buildSlotsText(StudentRequest req) {
        List<Map<String, String>> slots = extractSelectedSlots(req);
        String slotsText = slots.stream()
                .map(slot -> {
                    String dayName = slot.getOrDefault("dayName", "").trim();
                    String time = slot.getOrDefault("time", "").trim();
                    if (dayName.isEmpty() && time.isEmpty()) return "";
                    if (dayName.isEmpty()) return time;
                    if (time.isEmpty()) return dayName;
                    return dayName + " às " + time;
                })
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining(", "));
        if (!slotsText.isBlank()) {
            return slotsText;
        }
        return "horário não informado";
    }

    private String appendRequestMarker(String text, StudentRequest req) {
        if (text == null) {
            return null;
        }
        if (req == null || req.getId() == null) {
            return text;
        }
        return text + " [[REQ:" + req.getId() + "]]";
    }

    private boolean hasActiveRequestBetween(Long studentId, Long trainerId) {
        boolean hasPending = !requestRepo
                .findByStudentIdAndTrainerIdAndStatusOrderByCreatedAtDesc(studentId, trainerId, "PENDING")
                .isEmpty();
        boolean hasApproved = !requestRepo
                .findByStudentIdAndTrainerIdAndStatusOrderByCreatedAtDesc(studentId, trainerId, "APPROVED")
                .isEmpty();
        return hasPending || hasApproved;
    }

    private void sendStudentRemovedAfterBlockMessage(Long trainerId, Long studentId, StudentRequest referenceReq) {
        String trainerName = resolveTrainerName(referenceReq, trainerId);
        String studentName = resolveStudentName(referenceReq, studentId);
        String slotsText = buildSlotsText(referenceReq);
        ChatMessage msg = new ChatMessage();
        msg.setSenderId(trainerId);
        msg.setReceiverId(studentId);
        msg.setText(appendRequestMarker("Olá, " + studentName + ". " + trainerName + " cancelou o plano aprovado de " + slotsText + " e você não faz mais parte de Meus Alunos deste personal.", referenceReq));
        chatMessageRepo.save(msg);
    }

    private void sendPendingRejectedAfterBlockMessage(Long trainerId, Long studentId, StudentRequest pendingReq) {
        if (chatMessageRepo.hasTerminationMessage(trainerId, studentId)
                && !hasActiveRequestBetween(studentId, trainerId)) {
            return;
        }
        String trainerName = resolveTrainerName(pendingReq, trainerId);
        String slotsText = buildSlotsText(pendingReq);
        ChatMessage msg = new ChatMessage();
        msg.setSenderId(trainerId);
        msg.setReceiverId(studentId);
        String text = "❌ Sua solicitação foi recusada por " + trainerName + ". Horário: " + slotsText + ".";
        msg.setText(appendRequestMarker(text, pendingReq));
        chatMessageRepo.save(msg);
    }

    private void sendStudentWithPendingRemovedMessage(Long trainerId, Long studentId, StudentRequest referenceReq) {
        if (chatMessageRepo.hasTerminationMessage(trainerId, studentId)
                && !hasActiveRequestBetween(studentId, trainerId)) {
            return;
        }
        try {
            String trainerName = resolveTrainerName(referenceReq, trainerId);
            ChatMessage msg = new ChatMessage();
            msg.setSenderId(trainerId);
            msg.setReceiverId(studentId);
            msg.setText("A sua solicitação foi recusada pelo personal " + trainerName + " e você foi removido como aluno. Com isso, seus horários de atendimento foram cancelados.");
            chatMessageRepo.save(msg);
        } catch (Exception e) {
            System.err.println("Erro ao enviar mensagem Caso 3: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String slotKey(String dayName, String time) {
        return (dayName == null ? "" : dayName.trim().toLowerCase())
                + "|"
                + (time == null ? "" : time.trim().toLowerCase());
    }

    private List<fitmatch_api.model.TrainerSlot> computePreservedSlots(Long trainerId, Long studentId) {
        return slotRepo.findByTrainerId(trainerId)
                .stream()
                .filter(slot -> {
                    String state = slot.getState() == null ? "" : slot.getState().trim().toUpperCase();
                    return !"REQUEST".equals(state);
                })
                .toList();
    }

    private void restorePreservedSlots(Long trainerId, List<fitmatch_api.model.TrainerSlot> preservedSlots) {
        for (fitmatch_api.model.TrainerSlot slot : preservedSlots) {
            slotRepo.findByTrainerIdAndDayNameAndTime(trainerId, slot.getDayName(), slot.getTime())
                    .map(existing -> {
                        String preservedState = slot.getState() == null ? "" : slot.getState().trim();
                        String existingState = existing.getState() == null ? "" : existing.getState().trim();
                        if (!preservedState.isBlank()
                                && !preservedState.equalsIgnoreCase("REQUEST")
                                && !preservedState.equalsIgnoreCase(existingState)) {
                            existing.setState(preservedState);
                            return slotRepo.save(existing);
                        }
                        return existing;
                    })
                    .orElseGet(() -> {
                        fitmatch_api.model.TrainerSlot restored = new fitmatch_api.model.TrainerSlot();
                        restored.setTrainerId(trainerId);
                        restored.setDayName(slot.getDayName());
                        restored.setTime(slot.getTime());
                        restored.setState(slot.getState());
                        return slotRepo.save(restored);
                    });
        }
    }

    // Aluno se conecta ao personal (idempotente)
    @PostMapping
    public StudentTrainerConnection connect(@RequestBody ConnectDto dto) {
        if (dto.studentId() == null || dto.trainerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId e trainerId são obrigatórios");
        }
        return repo.findByStudentIdAndTrainerId(dto.studentId(), dto.trainerId())
                .orElseGet(() -> {
                    StudentTrainerConnection conn = new StudentTrainerConnection();
                    conn.setStudentId(dto.studentId());
                    conn.setTrainerId(dto.trainerId());
                    conn.setStudentName(dto.studentName() != null ? dto.studentName() : "Aluno");
                    conn.setTrainerName(dto.trainerName() != null ? dto.trainerName() : "Personal");
                    return repo.save(conn);
                });
    }

    // Retorna todas as conexões de um trainer (quem o segue)
    @GetMapping("/trainer/{trainerId}")
    public List<StudentTrainerConnection> getByTrainer(@PathVariable Long trainerId) {
        return repo.findByTrainerId(trainerId);
    }

        // Retorna apenas alunos com solicitação APROVADA para o trainer,
        // junto com os dados do plano aprovado para exibição no chat/dashboard.
        @GetMapping("/trainer/{trainerId}/approved")
        public List<Map<String, Object>> getApprovedByTrainer(@PathVariable Long trainerId) {
        Set<Long> blockedStudentIds = blockedStudentRepo.findByTrainerIdOrderByBlockedAtDesc(trainerId)
            .stream()
            .map(BlockedStudent::getStudentId)
            .collect(Collectors.toSet());

        List<StudentTrainerConnection> trainerConnections = repo.findByTrainerId(trainerId);

        return trainerConnections
                .stream()
                .filter(conn -> !blockedStudentIds.contains(conn.getStudentId()))
                .flatMap(conn -> requestRepo
                        .findByStudentIdAndTrainerIdAndStatusOrderByCreatedAtDesc(
                                conn.getStudentId(),
                                trainerId,
                                "APPROVED"
                        )
                        .stream()
                        .map(approved -> {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("id", approved.getId());
                            payload.put("studentId", conn.getStudentId());
                            payload.put("trainerId", trainerId);
                            payload.put("studentName", conn.getStudentName() != null ? conn.getStudentName() : approved.getStudentName());
                            payload.put("trainerName", conn.getTrainerName() != null ? conn.getTrainerName() : approved.getTrainerName());
                            payload.put("requestId", approved.getId());
                            payload.put("planType", approved.getPlanType());
                            payload.put("dayName", approved.getDayName());
                            payload.put("time", approved.getTime());
                            payload.put("daysJson", approved.getDaysJson());
                            payload.put("status", approved.getStatus());
                            payload.put("createdAt", approved.getCreatedAt());
                                payload.put("approvedAt", approved.getApprovedAt());
                            return payload;
                        }))
                .collect(Collectors.toList());
        }

    // Retorna todos os trainers que um aluno segue
    // Backfill automático: se trainerName for null (registro antigo), busca no cadastro de usuários
    @GetMapping("/student/{studentId}")
    public List<StudentTrainerConnection> getByStudent(@PathVariable Long studentId) {
        Set<Long> blockedTrainerIds = blockedStudentRepo.findByStudentId(studentId)
                .stream()
                .map(BlockedStudent::getTrainerId)
                .collect(Collectors.toSet());

        List<StudentTrainerConnection> connections = repo.findByStudentId(studentId);
        List<StudentTrainerConnection> visibleConnections = connections.stream()
                .filter(conn -> !blockedTrainerIds.contains(conn.getTrainerId()))
                .collect(Collectors.toList());

        for (StudentTrainerConnection conn : visibleConnections) {
            if (conn.getTrainerName() == null) {
                userRepo.findById(conn.getTrainerId()).ifPresent(trainer -> {
                    conn.setTrainerName(trainer.getName());
                    repo.save(conn);
                });
            }
        }
        return visibleConnections;
    }

    // Remove conexão
    @DeleteMapping("/{id}")
    public void disconnect(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conexão não encontrada");
        }
        repo.deleteById(id);
    }

    // Endpoints espelho para compatibilidade de bloqueio de aluno
    @Transactional
    @PostMapping("/trainer/{trainerId}/students/{studentId}/block")
        public BlockedStudent blockStudent(
            @PathVariable Long trainerId,
            @PathVariable Long studentId,
            @RequestParam(required = false) Long requestId
        ) {
        List<fitmatch_api.model.TrainerSlot> preservedSlots = computePreservedSlots(trainerId, studentId);
        final boolean hadActiveConnection = repo
                .findByStudentIdAndTrainerId(studentId, trainerId)
                .isPresent();

        // Busca PRIMEIRO as solicitações para determinar qual mensagem enviar
        List<StudentRequest> requests = requestRepo.findByStudentIdAndTrainerIdOrderByCreatedAtDesc(studentId, trainerId);
        List<StudentRequest> approvedRequests = requests.stream()
            .filter(req -> "APPROVED".equals(req.getStatus()))
            .toList();
        List<StudentRequest> activeRequests = requests.stream()
                .filter(req -> "APPROVED".equals(req.getStatus()) || "PENDING".equals(req.getStatus()))
                .toList();
        StudentRequest pendingRef = requests.stream()
                .filter(req -> "PENDING".equals(req.getStatus()))
                .findFirst()
                .orElse(null);
        StudentRequest requestRef = null;
        if (requestId != null) {
            requestRef = requests.stream()
                    .filter(req -> requestId.equals(req.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Solicitação informada não pertence a este aluno/personal"
                    ));
        }

        Set<Long> notifiedRequestIds = new java.util.HashSet<>();

        // ENVIAR MENSAGEM ANTES de modificar dados
        if (!activeRequests.isEmpty()) {
            // Garante aviso em todos os chats ativos (APPROVED/PENDING) do par,
            // para que cada solicitação aberta receba sua mensagem no próprio ciclo.
            for (StudentRequest activeReq : activeRequests) {
                if ("PENDING".equals(activeReq.getStatus()) && approvedRequests.isEmpty()) {
                    sendPendingRejectedAfterBlockMessage(trainerId, studentId, activeReq);
                } else {
                    sendStudentRemovedAfterBlockMessage(trainerId, studentId, activeReq);
                }

                if (activeReq.getId() != null) {
                    notifiedRequestIds.add(activeReq.getId());
                }
            }
        }

        if (requestRef != null) {
            Long requestRefId = requestRef.getId();
            if (requestRefId == null || !notifiedRequestIds.contains(requestRefId)) {
                if ("PENDING".equals(requestRef.getStatus()) && approvedRequests.isEmpty()) {
                    sendPendingRejectedAfterBlockMessage(trainerId, studentId, requestRef);
                } else {
                    sendStudentRemovedAfterBlockMessage(trainerId, studentId, requestRef);
                }

                if (requestRefId != null) {
                    notifiedRequestIds.add(requestRefId);
                }
            }
        } else if (hadActiveConnection) {
            // Fallback: garante aviso de remoção do aluno quando havia vínculo ativo,
            // mesmo que a solicitação aprovada não seja encontrada neste instante.
            StudentRequest latestReq = requests.isEmpty() ? null : requests.get(0);
            sendStudentRemovedAfterBlockMessage(trainerId, studentId, latestReq);
        } else if (pendingRef != null) {
            // Caso 1: Apenas solicitação pendente (não é aluno)
            sendPendingRejectedAfterBlockMessage(trainerId, studentId, pendingRef);
        }
        // Caso 4: Sem solicitação e sem conexão = nenhuma mensagem automática

        // DEPOIS atualizar estatuses e deletar conexão
        for (StudentRequest req : requests) {
            if (!"REJECTED".equals(req.getStatus())) {
                releaseRequestSlotsForBlockedStudent(req, studentId);
                req.setStatus("REJECTED");
                requestRepo.save(req);
            }
        }

        requestRepo.flush();
        rebuildTrainerSlotsFromApprovedRequests(trainerId);
    restorePreservedSlots(trainerId, preservedSlots);
        repo.deleteByStudentIdAndTrainerId(studentId, trainerId);

        // Criar ou retornar o bloqueio
        return blockedStudentRepo.findByTrainerIdAndStudentId(trainerId, studentId)
                .orElseGet(() -> {
                    BlockedStudent block = new BlockedStudent();
                    block.setTrainerId(trainerId);
                    block.setStudentId(studentId);
                    return blockedStudentRepo.save(block);
                });
    }

    @DeleteMapping("/trainer/{trainerId}/students/{studentId}/block")
    public void unblockStudent(@PathVariable Long trainerId, @PathVariable Long studentId) {
        blockedStudentService.unblockStudent(trainerId, studentId);
    }

    @GetMapping("/trainer/{trainerId}/students/blocked")
    public List<BlockedStudent> getBlockedStudents(@PathVariable Long trainerId) {
        return blockedStudentRepo.findByTrainerIdOrderByBlockedAtDesc(trainerId);
    }

    record ConnectDto(Long studentId, Long trainerId, String studentName, String trainerName) {}
}
