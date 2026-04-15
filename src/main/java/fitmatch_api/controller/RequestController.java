package fitmatch_api.controller;
import fitmatch_api.model.BlockedStudent;
import fitmatch_api.model.ChatMessage;
import fitmatch_api.model.StudentRequest;
import fitmatch_api.model.StudentTrainerConnection;
import fitmatch_api.repository.StudentRequestRepository;
import fitmatch_api.repository.BlockedStudentRepository;
import fitmatch_api.repository.ChatMessageRepository;
import fitmatch_api.repository.TrainerSlotRepository;
import fitmatch_api.repository.StudentTrainerConnectionRepository;
import fitmatch_api.model.TrainerSlot;
import fitmatch_api.service.BlockedStudentService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/requests")
@CrossOrigin(origins = "*")
public class RequestController {

    private final StudentRequestRepository requestRepo;
    private final TrainerSlotRepository slotRepo;
    private final BlockedStudentRepository blockedStudentRepo;
    private final StudentTrainerConnectionRepository connectionRepo;
    private final ChatMessageRepository chatMessageRepo;
    private final BlockedStudentService blockedStudentService;
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
        private static final Pattern SLOT_OBJECT_PATTERN = Pattern.compile("\\{[^\\{\\}]*\\}");

        private static final String MANUAL_ONCE_PREFIX = "__ONCE__:";
        private static final String MANUAL_ONCE_SEPARATOR = "|";

    public RequestController(
            StudentRequestRepository requestRepo,
            TrainerSlotRepository slotRepo,
            BlockedStudentRepository blockedStudentRepo,
            StudentTrainerConnectionRepository connectionRepo,
            ChatMessageRepository chatMessageRepo,
            BlockedStudentService blockedStudentService
    ) {
        this.requestRepo = requestRepo;
        this.slotRepo = slotRepo;
        this.blockedStudentRepo = blockedStudentRepo;
        this.connectionRepo = connectionRepo;
        this.chatMessageRepo = chatMessageRepo;
        this.blockedStudentService = blockedStudentService;
    }

    private List<Map<String, String>> parseSlotsFromJson(String rawJson) {
        List<Map<String, String>> slots = new ArrayList<>();

        Matcher objectMatcher = SLOT_OBJECT_PATTERN.matcher(rawJson);
        while (objectMatcher.find()) {
            String objectText = objectMatcher.group();
            String dayName = extractJsonField(objectText, "dayName").trim();
            String time = extractJsonField(objectText, "time").trim();
            String dateIso = normalizeDateIso(extractJsonField(objectText, "dateIso"));

            if (!dayName.isEmpty() && !time.isEmpty()) {
                java.util.HashMap<String, String> slot = new java.util.HashMap<>();
                slot.put("dayName", dayName);
                slot.put("time", time);
                slot.put("dateIso", dateIso);
                slots.add(slot);
            }
        }

        if (!slots.isEmpty()) {
            return slots;
        }

        Matcher matcher = DAY_TIME_PATTERN_DOUBLE.matcher(rawJson);
        while (matcher.find()) {
            String dayName = matcher.group(1).trim();
            String time = matcher.group(2).trim();
            if (!dayName.isEmpty() && !time.isEmpty()) {
                java.util.HashMap<String, String> slot = new java.util.HashMap<>();
                slot.put("dayName", dayName);
                slot.put("time", time);
                slot.put("dateIso", "");
                slots.add(slot);
            }
        }

        Matcher reversedMatcher = DAY_TIME_PATTERN_DOUBLE_REVERSED.matcher(rawJson);
        while (reversedMatcher.find()) {
            String dayName = reversedMatcher.group(2).trim();
            String time = reversedMatcher.group(1).trim();
            if (!dayName.isEmpty() && !time.isEmpty()) {
                java.util.HashMap<String, String> slot = new java.util.HashMap<>();
                slot.put("dayName", dayName);
                slot.put("time", time);
                slot.put("dateIso", "");
                slots.add(slot);
            }
        }

        Matcher singleMatcher = DAY_TIME_PATTERN_SINGLE.matcher(rawJson);
        while (singleMatcher.find()) {
            String dayName = singleMatcher.group(1).trim();
            String time = singleMatcher.group(2).trim();
            if (!dayName.isEmpty() && !time.isEmpty()) {
                java.util.HashMap<String, String> slot = new java.util.HashMap<>();
                slot.put("dayName", dayName);
                slot.put("time", time);
                slot.put("dateIso", "");
                slots.add(slot);
            }
        }

        Matcher singleReversedMatcher = DAY_TIME_PATTERN_SINGLE_REVERSED.matcher(rawJson);
        while (singleReversedMatcher.find()) {
            String dayName = singleReversedMatcher.group(2).trim();
            String time = singleReversedMatcher.group(1).trim();
            if (!dayName.isEmpty() && !time.isEmpty()) {
                java.util.HashMap<String, String> slot = new java.util.HashMap<>();
                slot.put("dayName", dayName);
                slot.put("time", time);
                slot.put("dateIso", "");
                slots.add(slot);
            }
        }

        return slots;
    }

    private String extractJsonField(String objectText, String fieldName) {
        if (objectText == null || objectText.isBlank() || fieldName == null || fieldName.isBlank()) {
            return "";
        }

        String escapedField = Pattern.quote(fieldName);

        Pattern doubleQuoted = Pattern.compile("\"" + escapedField + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher dq = doubleQuoted.matcher(objectText);
        if (dq.find()) {
            return dq.group(1);
        }

        Pattern singleQuoted = Pattern.compile("'" + escapedField + "'\\s*:\\s*'([^']*)'");
        Matcher sq = singleQuoted.matcher(objectText);
        if (sq.find()) {
            return sq.group(1);
        }

        return "";
    }

    private String normalizeDateIso(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return "";
        }
        return value.length() >= 10 ? value.substring(0, 10) : value;
    }

    private boolean isManualOnceStoredDay(String storedDay) {
        return storedDay != null && storedDay.startsWith(MANUAL_ONCE_PREFIX);
    }

    private String decodeManualOnceDateIso(String storedDay) {
        if (!isManualOnceStoredDay(storedDay)) {
            return "";
        }
        String payload = storedDay.substring(MANUAL_ONCE_PREFIX.length());
        int sep = payload.indexOf(MANUAL_ONCE_SEPARATOR);
        if (sep <= 0) {
            return "";
        }
        return normalizeDateIso(payload.substring(0, sep));
    }

    private String decodeManualOnceDayName(String storedDay) {
        if (!isManualOnceStoredDay(storedDay)) {
            return storedDay == null ? "" : storedDay;
        }
        String payload = storedDay.substring(MANUAL_ONCE_PREFIX.length());
        int sep = payload.indexOf(MANUAL_ONCE_SEPARATOR);
        if (sep < 0 || sep + 1 >= payload.length()) {
            return "";
        }
        return payload.substring(sep + 1);
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private String normalizeDayName(String dayName) {
        return normalizeText(dayName);
    }

    private String normalizeTime(String time) {
        return normalizeText(time).replaceAll("\\s+", "");
    }

    private String slotKey(String dayName, String time) {
        return normalizeDayName(dayName) + "|" + normalizeTime(time);
    }

    private boolean isSlotBlockedForSelection(Long trainerId, Map<String, String> selectedSlot) {
        String selectedDay = normalizeDayName(selectedSlot.getOrDefault("dayName", ""));
        String selectedTime = normalizeTime(selectedSlot.getOrDefault("time", ""));
        String selectedDateIso = normalizeDateIso(selectedSlot.getOrDefault("dateIso", ""));
        if (selectedDay.isBlank() || selectedTime.isBlank()) {
            return true;
        }

        List<TrainerSlot> blockedSlots = slotRepo.findByTrainerId(trainerId);
        boolean hasWeeklyBlock = false;
        boolean hasOnceBlock = false;
        boolean hasOnceUnblock = false;

        for (TrainerSlot blockedSlot : blockedSlots) {
            String blockedTime = normalizeTime(blockedSlot.getTime());
            if (!selectedTime.equals(blockedTime)) {
                continue;
            }

            String blockedState = blockedSlot.getState() == null
                    ? ""
                    : blockedSlot.getState().trim().toUpperCase(Locale.ROOT);

            String blockedDayRaw = blockedSlot.getDayName();
            if (isManualOnceStoredDay(blockedDayRaw)) {
                String blockedDateIso = decodeManualOnceDateIso(blockedDayRaw);
                String blockedDay = normalizeDayName(decodeManualOnceDayName(blockedDayRaw));
                if (selectedDay.equals(blockedDay)
                        && !selectedDateIso.isBlank()
                        && selectedDateIso.equals(blockedDateIso)) {
                    if ("UNBLOCK_ONCE".equals(blockedState)) {
                        hasOnceUnblock = true;
                    } else {
                        hasOnceBlock = true;
                    }
                }
                continue;
            }

            String blockedDay = normalizeDayName(blockedDayRaw);
            if (selectedDay.equals(blockedDay)) {
                hasWeeklyBlock = true;
            }
        }

        if (hasOnceBlock) {
            return true;
        }
        if (hasWeeklyBlock && hasOnceUnblock) {
            return false;
        }
        return hasWeeklyBlock;
    }

    private DayOfWeek weekdayFromPt(String dayName) {
        String normalized = normalizeDayName(dayName);
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

    private int[] parseHourMinute(String time) {
        if (time == null) return null;
        String normalized = normalizeTime(time);
        String[] parts = normalized.split(":");
        if (parts.length < 2) return null;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return new int[]{hour, minute};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime nextOccurrence(LocalDateTime base, DayOfWeek weekday, int hour, int minute) {
        LocalDateTime sameDayAtTime = LocalDateTime.of(
                base.getYear(),
                base.getMonthValue(),
                base.getDayOfMonth(),
                hour,
                minute
        );
        int deltaDays = weekday.getValue() - base.getDayOfWeek().getValue();
        if (deltaDays < 0) {
            deltaDays += 7;
        }
        LocalDateTime candidate = sameDayAtTime.plusDays(deltaDays);
        if (candidate.isBefore(base)) {
            candidate = candidate.plusDays(7);
        }
        return candidate;
    }

    private String normalizePlanType(String rawPlanType) {
        if (rawPlanType == null || rawPlanType.isBlank()) {
            return "DIARIO";
        }
        String normalized = rawPlanType.trim().toUpperCase(Locale.ROOT);
        if ("DIARIO".equals(normalized)
                || "SEMANAL".equals(normalized)
                || "MENSAL".equals(normalized)) {
            return normalized;
        }
        return "DIARIO";
    }

    private LocalDateTime resolveSlotStartAt(Map<String, String> slot, LocalDateTime anchor) {
        if (slot == null) {
            return null;
        }

        DayOfWeek weekday = weekdayFromPt(slot.getOrDefault("dayName", ""));
        int[] hm = parseHourMinute(slot.getOrDefault("time", ""));
        if (weekday == null || hm == null) {
            return null;
        }

        String slotDateIso = normalizeDateIso(slot.getOrDefault("dateIso", ""));
        if (!slotDateIso.isBlank()) {
            try {
                LocalDate parsedDate = LocalDate.parse(slotDateIso);
                return LocalDateTime.of(
                        parsedDate.getYear(),
                        parsedDate.getMonthValue(),
                        parsedDate.getDayOfMonth(),
                        hm[0],
                        hm[1]
                );
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }

        LocalDateTime candidate = nextOccurrence(anchor, weekday, hm[0], hm[1]);
        if (weekday.getValue() == anchor.getDayOfWeek().getValue()) {
            LocalDateTime sameDayScheduled = LocalDateTime.of(
                    anchor.getYear(),
                    anchor.getMonthValue(),
                    anchor.getDayOfMonth(),
                    hm[0],
                    hm[1]
            );
            if (sameDayScheduled.isBefore(anchor)) {
                candidate = sameDayScheduled;
            }
        }

        return candidate;
    }

    private LocalDateTime resolvePlanWindowEnd(LocalDateTime anchor, String planType) {
        if ("SEMANAL".equals(planType)) {
            return LocalDateTime.of(anchor.toLocalDate().plusDays(7), LocalTime.MAX);
        }
        if ("MENSAL".equals(planType)) {
            return LocalDateTime.of(anchor.toLocalDate().plusMonths(1), LocalTime.MAX);
        }
        return LocalDateTime.of(anchor.toLocalDate().plusDays(1), LocalTime.MAX);
    }

    private void validateSelectedSlotsByPlanWindow(
            List<Map<String, String>> selectedSlots,
            String planType,
            LocalDateTime anchor
    ) {
        if (!("SEMANAL".equals(planType) || "MENSAL".equals(planType))) {
            return;
        }

        if ("SEMANAL".equals(planType)) {
            Set<LocalDate> selectedDates = new HashSet<>();
            for (Map<String, String> slot : selectedSlots) {
                LocalDateTime slotStart = resolveSlotStartAt(slot, anchor);
                if (slotStart == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Horário selecionado inválido"
                    );
                }
                selectedDates.add(slotStart.toLocalDate());
                if (selectedDates.size() > 7) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Plano semanal permite no máximo 7 dias selecionados"
                    );
                }
            }
            return;
        }

        LocalDateTime windowEnd = resolvePlanWindowEnd(anchor, planType);
        for (Map<String, String> slot : selectedSlots) {
            LocalDateTime slotStart = resolveSlotStartAt(slot, anchor);
            if (slotStart == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Horário selecionado inválido"
                );
            }

            if (slotStart.isBefore(anchor) || slotStart.isAfter(windowEnd)) {
                if ("SEMANAL".equals(planType)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Plano semanal permite solicitar horários em até 7 dias"
                    );
                }
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Plano mensal permite solicitar horários em até 1 mês"
                );
            }
        }
    }

    private LocalDateTime resolveSelectionAnchor(
            List<Map<String, String>> selectedSlots,
            LocalDateTime fallbackAnchor
    ) {
        if (selectedSlots == null || selectedSlots.isEmpty()) {
            return fallbackAnchor;
        }

        for (Map<String, String> slot : selectedSlots) {
            LocalDateTime slotStart = resolveSlotStartAt(slot, fallbackAnchor);
            if (slotStart == null) {
                continue;
            }
            return LocalDateTime.of(slotStart.toLocalDate(), LocalTime.MIN);
        }
        return fallbackAnchor;
    }

    private LocalDateTime resolveFirstSessionStartAt(StudentRequest req) {
        if (req == null) return null;
        LocalDateTime anchor = req.getCreatedAt() != null ? req.getCreatedAt() : LocalDateTime.now();
        List<Map<String, String>> slots = extractSelectedSlots(req);
        if (slots.isEmpty()) {
            return null;
        }

        LocalDateTime first = null;
        for (Map<String, String> slot : slots) {
            DayOfWeek weekday = weekdayFromPt(slot.getOrDefault("dayName", ""));
            int[] hm = parseHourMinute(slot.getOrDefault("time", ""));
            if (weekday == null || hm == null) continue;

            LocalDateTime candidate = null;
            String slotDateIso = normalizeDateIso(slot.getOrDefault("dateIso", ""));
            if (!slotDateIso.isBlank()) {
                try {
                    LocalDate parsedDate = LocalDate.parse(slotDateIso);
                    candidate = LocalDateTime.of(
                            parsedDate.getYear(),
                            parsedDate.getMonthValue(),
                            parsedDate.getDayOfMonth(),
                            hm[0],
                            hm[1]
                    );
                } catch (DateTimeParseException ignored) {
                    candidate = null;
                }
            }

            if (candidate == null) {
                candidate = nextOccurrence(anchor, weekday, hm[0], hm[1]);
                if (weekday.getValue() == anchor.getDayOfWeek().getValue()) {
                    LocalDateTime sameDayScheduled = LocalDateTime.of(
                            anchor.getYear(),
                            anchor.getMonthValue(),
                            anchor.getDayOfMonth(),
                            hm[0],
                            hm[1]
                    );
                    if (sameDayScheduled.isBefore(anchor)) {
                        candidate = sameDayScheduled;
                    }
                }
            }

            if (first == null || candidate.isBefore(first)) {
                first = candidate;
            }
        }

        return first;
    }

    private LocalDateTime resolveLastSessionStartAt(StudentRequest req) {
        if (req == null) return null;
        LocalDateTime anchor = req.getCreatedAt() != null ? req.getCreatedAt() : LocalDateTime.now();
        List<Map<String, String>> slots = extractSelectedSlots(req);
        if (slots.isEmpty()) {
            return null;
        }

        LocalDateTime last = null;
        for (Map<String, String> slot : slots) {
            DayOfWeek weekday = weekdayFromPt(slot.getOrDefault("dayName", ""));
            int[] hm = parseHourMinute(slot.getOrDefault("time", ""));
            if (weekday == null || hm == null) continue;

            LocalDateTime candidate = null;
            String slotDateIso = normalizeDateIso(slot.getOrDefault("dateIso", ""));
            if (!slotDateIso.isBlank()) {
                try {
                    LocalDate parsedDate = LocalDate.parse(slotDateIso);
                    candidate = LocalDateTime.of(
                            parsedDate.getYear(),
                            parsedDate.getMonthValue(),
                            parsedDate.getDayOfMonth(),
                            hm[0],
                            hm[1]
                    );
                } catch (DateTimeParseException ignored) {
                    candidate = null;
                }
            }

            if (candidate == null) {
                candidate = nextOccurrence(anchor, weekday, hm[0], hm[1]);
                if (weekday.getValue() == anchor.getDayOfWeek().getValue()) {
                    LocalDateTime sameDayScheduled = LocalDateTime.of(
                            anchor.getYear(),
                            anchor.getMonthValue(),
                            anchor.getDayOfMonth(),
                            hm[0],
                            hm[1]
                    );
                    if (sameDayScheduled.isBefore(anchor)) {
                        candidate = sameDayScheduled;
                    }
                }
            }

            if (last == null || candidate.isAfter(last)) {
                last = candidate;
            }
        }

        return last;
    }

    private boolean isPendingExpired(StudentRequest req, LocalDateTime now) {
        if (req == null || !"PENDING".equals(req.getStatus())) {
            return false;
        }
        String planType = normalizePlanType(req.getPlanType());
        LocalDateTime threshold;

        if ("DIARIO".equals(planType)) {
            threshold = resolveLastSessionStartAt(req);
        } else {
            threshold = resolveFirstSessionStartAt(req);
        }

        if (threshold == null) {
            return false;
        }

        return !now.isBefore(threshold);
    }

    private int expireStalePendingRequests(List<StudentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        List<StudentRequest> changed = new ArrayList<>();
        for (StudentRequest req : requests) {
            if (isPendingExpired(req, now)) {
                sendPendingExpiredByTimeoutMessage(req);
                req.setStatus("REJECTED");
                changed.add(req);
            }
        }

        if (!changed.isEmpty()) {
            requestRepo.saveAll(changed);
        }

        return changed.size();
    }

    private void sendPendingExpiredByTimeoutMessage(StudentRequest req) {
        if (req == null || req.getStudentId() == null || req.getTrainerId() == null) {
            return;
        }

        String trainerName = resolveTrainerName(req, req.getTrainerId());
        String slotsText = buildSlotsText(req);

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(req.getTrainerId());
        msg.setReceiverId(req.getStudentId());
        msg.setText(appendRequestMarker(
                "O personal " + trainerName +
                        " não respondeu a sua solicitação para " + slotsText +
                        " e o tempo expirou. Envie uma nova solicitação.",
                req
        ));
        chatMessageRepo.save(msg);
    }

    private List<Map<String, String>> extractSelectedSlots(SendRequestDto dto) {
        List<Map<String, String>> slots = new ArrayList<>();
        if (dto.daysJson() != null && !dto.daysJson().isBlank()) {
            slots = parseSlotsFromJson(dto.daysJson());

            if (slots.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato inválido para daysJson");
            }
        }

        if (slots.isEmpty()) {
            java.util.HashMap<String, String> slot = new java.util.HashMap<>();
            slot.put("dayName", dto.dayName());
            slot.put("time", dto.time());
            slot.put("dateIso", "");
            slots.add(slot);
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
                java.util.HashMap<String, String> slot = new java.util.HashMap<>();
                slot.put("dayName", dayName);
                slot.put("time", time);
                slot.put("dateIso", "");
                slots.add(slot);
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

    private boolean hasStudentScheduleConflict(Long studentId, List<Map<String, String>> selectedSlots) {
        Set<String> requestedSlotKeys = selectedSlots.stream()
                .map(slot -> slotKey(
                        slot.getOrDefault("dayName", ""),
                        slot.getOrDefault("time", "")
                ))
                .filter(key -> !key.equals("|"))
                .collect(Collectors.toSet());

        if (requestedSlotKeys.isEmpty()) {
            return false;
        }

        List<StudentRequest> activeRequests = requestRepo.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .filter(req -> "PENDING".equals(req.getStatus()) || "APPROVED".equals(req.getStatus()))
                .collect(Collectors.toList());

        Set<String> activeSlotKeys = new HashSet<>();
        for (StudentRequest req : activeRequests) {
            for (Map<String, String> slot : extractSelectedSlots(req)) {
                String key = slotKey(
                        slot.getOrDefault("dayName", ""),
                        slot.getOrDefault("time", "")
                );
                if (!key.equals("|")) {
                    activeSlotKeys.add(key);
                }
            }
        }

        return requestedSlotKeys.stream().anyMatch(activeSlotKeys::contains);
    }

    // Aluno envia solicitação de horário ao personal
    @PostMapping
    public StudentRequest sendRequest(@RequestBody SendRequestDto dto) {
        if (dto.trainerId() == null || dto.studentId() == null
                || dto.dayName() == null || dto.time() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campos obrigatórios ausentes");
        }

        List<StudentRequest> pairPendingRequests = requestRepo
            .findByStudentIdAndTrainerIdAndStatusOrderByCreatedAtDesc(dto.studentId(), dto.trainerId(), "PENDING");
        expireStalePendingRequests(pairPendingRequests);

        boolean hasPendingForTrainer = pairPendingRequests
            .stream()
            .anyMatch(req -> "PENDING".equals(req.getStatus()));
        if (hasPendingForTrainer) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Você já possui uma solicitação pendente para este personal"
            );
        }

        List<Map<String, String>> selectedSlots = extractSelectedSlots(dto);
        String normalizedPlanType = normalizePlanType(dto.planType());
        LocalDateTime requestAnchor = resolveSelectionAnchor(
            selectedSlots,
            LocalDate.now().atStartOfDay()
        );
        validateSelectedSlotsByPlanWindow(selectedSlots, normalizedPlanType, requestAnchor);

        if (blockedStudentRepo.existsByTrainerIdAndStudentId(dto.trainerId(), dto.studentId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Este personal bloqueou novas solicitações deste aluno");
        }

        if (hasStudentScheduleConflict(dto.studentId(), selectedSlots)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Você já possui solicitação/plano ativo neste horário com outro personal");
        }

        for (Map<String, String> slot : selectedSlots) {
            String dayName = slot.getOrDefault("dayName", "").trim();
            String time = slot.getOrDefault("time", "").trim();
            if (dayName.isEmpty() || time.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horário selecionado inválido");
            }

            boolean slotBlocked = isSlotBlockedForSelection(dto.trainerId(), slot);
            if (slotBlocked) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O horário " + dayName + " às " + time + " não está disponível para este personal");
            }
        }

        StudentRequest req = new StudentRequest();
        req.setTrainerId(dto.trainerId());
        req.setStudentId(dto.studentId());
        req.setStudentName(dto.studentName() != null ? dto.studentName() : "Aluno");
        req.setTrainerName(dto.trainerName() != null ? dto.trainerName() : "Personal");
        req.setDayName(dto.dayName());
        req.setTime(dto.time());
        req.setStatus("PENDING");
        req.setPlanType(normalizedPlanType);
        req.setDaysJson(dto.daysJson());
        return requestRepo.save(req);
    }

    private boolean slotIsUsedByAnotherApprovedRequest(StudentRequest currentRequest, String dayName, String time) {
        return requestRepo.findByTrainerIdAndStatus(currentRequest.getTrainerId(), "APPROVED")
                .stream()
                .filter(other -> other.getId() != null && !other.getId().equals(currentRequest.getId()))
                .filter(this::shouldPersistRequestSlot)
            .filter(other -> other.getStudentId() != null
                && currentRequest.getStudentId() != null
                && !other.getStudentId().equals(currentRequest.getStudentId()))
                .anyMatch(other -> extractSelectedSlots(other)
                        .stream()
                        .anyMatch(slot -> dayName.equals(slot.getOrDefault("dayName", "").trim())
                                && time.equals(slot.getOrDefault("time", "").trim())));
    }

    private void releaseRequestSlots(StudentRequest req) {
        List<Map<String, String>> selectedSlots = extractSelectedSlots(req);
        for (Map<String, String> slot : selectedSlots) {
            String dayName = slot.getOrDefault("dayName", "").trim();
            String time = slot.getOrDefault("time", "").trim();
            if (!dayName.isEmpty()
                    && !time.isEmpty()
                    && !slotIsUsedByAnotherApprovedRequest(req, dayName, time)) {
                slotRepo.deleteByTrainerIdAndDayNameAndTimeAndState(
                        req.getTrainerId(),
                        dayName,
                        time,
                        "REQUEST"
                );
            }
        }
    }

    private void releaseApprovedStudentResources(Long trainerId, Long studentId, boolean removeConnection) {
        List<StudentRequest> requests = requestRepo
                .findByStudentIdAndTrainerIdOrderByCreatedAtDesc(studentId, trainerId);

        List<StudentRequest> toReleaseSlots = new ArrayList<>();

        // 1) Marca todas as solicitações do aluno como REJECTED
        // (garante que nenhuma solicitação do próprio aluno bloqueie a liberação de horário)
        for (StudentRequest req : requests) {
            boolean changed = false;

            if (!"REJECTED".equals(req.getStatus())) {
                toReleaseSlots.add(req);
                req.setStatus("REJECTED");
                changed = true;
            }

            if (changed) {
                requestRepo.save(req);
            }
        }

        // 2) Libera todos os horários das solicitações que foram encerradas
        for (StudentRequest req : toReleaseSlots) {
            releaseRequestSlots(req);
        }

        requestRepo.flush();
        rebuildTrainerSlotsFromApprovedRequests(trainerId);

        if (removeConnection) {
            connectionRepo.deleteByStudentIdAndTrainerId(studentId, trainerId);
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
                            TrainerSlot blockedSlot = new TrainerSlot();
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

    private String resolveStudentName(StudentRequest req, Long studentId) {
        if (req != null && req.getStudentName() != null && !req.getStudentName().isBlank()) {
            return req.getStudentName();
        }
        return "Aluno #" + studentId;
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

    private void sendStudentCancelledOwnRequestMessage(StudentRequest req, String reason) {
        if (req == null || req.getStudentId() == null || req.getTrainerId() == null) {
            return;
        }

        String trainerName = resolveTrainerName(req, req.getTrainerId());
        String normalizedReason = reason == null ? "" : reason.trim().toUpperCase(Locale.ROOT);

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(req.getStudentId());
        msg.setReceiverId(req.getTrainerId());
        String text;
        if ("KEEP_PLAN".equals(normalizedReason) || "RENEW".equals(normalizedReason)) {
            text = "Olá, " + trainerName
                    + ". Decidi manter meu plano e uma nova solicitação chegará para você em breve.";
        } else if ("CHANGE_PLAN".equals(normalizedReason) || "CHANGE".equals(normalizedReason)) {
            text = "Olá, " + trainerName
                + ". Decidi mudar meu plano e uma nova solicitação chegará para você em breve.";
        } else {
            text = "Olá, " + trainerName
                    + ". Decidi não renovar e nem mudar o meu plano no momento. "
                    + "Obrigado pelo suporte até aqui.";
        }
        msg.setText(appendRequestMarker(text, req));
        chatMessageRepo.save(msg);
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
        String slotsText = referenceReq != null
                ? buildSlotsText(referenceReq)
                : "horário não informado";
        ChatMessage msg = new ChatMessage();
        msg.setSenderId(trainerId);
        msg.setReceiverId(studentId);
        String text;
        if (referenceReq != null) {
            text = "Olá, " + studentName + ". " + trainerName + " cancelou sua solicitação/plano de " + slotsText + " e você não faz mais parte de Meus Alunos deste personal.";
        } else {
            text = "Olá, " + studentName + ". " + trainerName + " removeu você de Meus Alunos e encerrou os atendimentos ativos.";
        }
        msg.setText(appendRequestMarker(text, referenceReq));
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

    private Set<String> toSlotKeys(List<Map<String, String>> slots) {
        return slots.stream()
                .map(slot -> slotKey(
                        slot.getOrDefault("dayName", ""),
                        slot.getOrDefault("time", "")
                ))
                .filter(key -> !key.equals("|"))
                .collect(Collectors.toSet());
    }

    private List<TrainerSlot> computePreservedSlots(Long trainerId, Long studentId) {
        return slotRepo.findByTrainerId(trainerId)
                .stream()
                .filter(slot -> {
                    String state = slot.getState() == null ? "" : slot.getState().trim().toUpperCase(Locale.ROOT);
                    return !"REQUEST".equals(state);
                })
                .toList();
    }

    private void restorePreservedSlots(Long trainerId, List<TrainerSlot> preservedSlots) {
        for (TrainerSlot slot : preservedSlots) {
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
                        TrainerSlot restored = new TrainerSlot();
                        restored.setTrainerId(trainerId);
                        restored.setDayName(slot.getDayName());
                        restored.setTime(slot.getTime());
                        restored.setState(slot.getState());
                        return slotRepo.save(restored);
                    });
        }
    }

    // Personal vê solicitações PENDENTES
    @GetMapping("/trainer/{trainerId}")
    public List<StudentRequest> getTrainerRequests(@PathVariable Long trainerId) {
        List<StudentRequest> requests = requestRepo.findByTrainerIdAndStatusOrderByCreatedAtDesc(trainerId, "PENDING");
        expireStalePendingRequests(requests);
        return requests.stream()
                .filter(req -> "PENDING".equals(req.getStatus()))
                .toList();
    }

    // Personal vê solicitações APROVADAS (alunos confirmados)
    @GetMapping("/trainer/{trainerId}/approved")
    public List<StudentRequest> getApprovedTrainerRequests(@PathVariable Long trainerId) {
        return requestRepo.findByTrainerIdAndStatusOrderByCreatedAtDesc(trainerId, "APPROVED");
    }

    // Personal vê histórico completo de solicitações (PENDING, APPROVED, REJECTED)
    @GetMapping("/trainer/{trainerId}/all")
    public List<StudentRequest> getAllTrainerRequests(@PathVariable Long trainerId) {
        List<StudentRequest> requests = requestRepo.findByTrainerIdOrderByCreatedAtDesc(trainerId);
        if (expireStalePendingRequests(requests) > 0) {
            return requestRepo.findByTrainerIdOrderByCreatedAtDesc(trainerId);
        }
        return requests;
    }

    // Aluno vê suas próprias solicitações (todos os status)
    @GetMapping("/student/{studentId}")
    public List<StudentRequest> getStudentRequests(@PathVariable Long studentId) {
        return getStudentRequestsHistory(studentId);
    }

    // Alias explícito para histórico completo do aluno
    @GetMapping("/student/{studentId}/all")
    public List<StudentRequest> getStudentRequestsAll(@PathVariable Long studentId) {
        return getStudentRequestsHistory(studentId);
    }

    private List<StudentRequest> getStudentRequestsHistory(Long studentId) {
        List<StudentRequest> requests = requestRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
        expireStalePendingRequests(requests);
        Set<Long> blockedTrainerIds = blockedStudentRepo.findByStudentId(studentId)
                .stream()
                .map(BlockedStudent::getTrainerId)
                .collect(Collectors.toSet());

        List<StudentRequest> changedRequests = new ArrayList<>();
        for (StudentRequest req : requests) {
            if (blockedTrainerIds.contains(req.getTrainerId())
                    && "PENDING".equals(req.getStatus())) {
                req.setStatus("REJECTED");
                changedRequests.add(req);
            }
        }

        if (!changedRequests.isEmpty()) {
            requestRepo.saveAll(changedRequests);
        }

        // Filtra registros ocultos para o aluno
        requests.removeIf(req -> Boolean.TRUE.equals(req.getHiddenForStudent()));

        return requests;
    }

    // Aluno oculta solicitação apenas da sua própria lista (sem deletar do banco)
    @PatchMapping("/{id}/hide-for-student")
    public StudentRequest hideRequestForStudent(@PathVariable Long id) {
        return hideRequestForStudentInternal(id);
    }

    @PostMapping("/{id}/hide-for-student")
    public StudentRequest hideRequestForStudentPost(@PathVariable Long id) {
        return hideRequestForStudentInternal(id);
    }

    @DeleteMapping("/{id}/hide-for-student")
    public StudentRequest hideRequestForStudentDelete(@PathVariable Long id) {
        return hideRequestForStudentInternal(id);
    }

    private StudentRequest hideRequestForStudentInternal(Long id) {
        StudentRequest req = requestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada"));
        req.setHiddenForStudent(true);
        return requestRepo.save(req);
    }

    // Personal aprova ou rejeita
    @PatchMapping("/{id}/status")
    public StudentRequest updateStatus(@PathVariable Long id, @RequestBody UpdateStatusDto dto) {
        StudentRequest req = requestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada"));
        if (!List.of("APPROVED", "REJECTED").contains(dto.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status inválido: use APPROVED ou REJECTED");
        }

        if ("APPROVED".equals(dto.status())) {
            if (shouldPersistRequestSlot(req)) {
                List<Map<String, String>> selectedSlots = extractSelectedSlots(req);
                for (Map<String, String> slot : selectedSlots) {
                    String dayName = slot.getOrDefault("dayName", "").trim();
                    String time = slot.getOrDefault("time", "").trim();
                    if (dayName.isEmpty() || time.isEmpty()) continue;

                    slotRepo.findByTrainerIdAndDayNameAndTime(req.getTrainerId(), dayName, time)
                            .orElseGet(() -> {
                                TrainerSlot blockedSlot = new TrainerSlot();
                                blockedSlot.setTrainerId(req.getTrainerId());
                                blockedSlot.setDayName(dayName);
                                blockedSlot.setTime(time);
                                blockedSlot.setState("REQUEST");
                                return slotRepo.save(blockedSlot);
                            });
                }
            } else {
                List<Map<String, String>> selectedSlots = extractSelectedSlots(req);
                for (Map<String, String> slot : selectedSlots) {
                    String dayName = slot.getOrDefault("dayName", "").trim();
                    String time = slot.getOrDefault("time", "").trim();
                    if (dayName.isEmpty() || time.isEmpty()) continue;

                    slotRepo.deleteByTrainerIdAndDayNameAndTimeAndState(
                            req.getTrainerId(),
                            dayName,
                            time,
                            "REQUEST"
                    );
                }
            }

            connectionRepo.findByStudentIdAndTrainerId(req.getStudentId(), req.getTrainerId())
                    .orElseGet(() -> {
                        StudentTrainerConnection connection = new StudentTrainerConnection();
                        connection.setStudentId(req.getStudentId());
                        connection.setTrainerId(req.getTrainerId());
                        connection.setStudentName(req.getStudentName());
                        connection.setTrainerName(req.getTrainerName());
                        return connectionRepo.save(connection);
                    });

            req.setApprovedAt(LocalDateTime.now());
        } else {
            releaseRequestSlots(req);
        }

        req.setStatus(dto.status());
        return requestRepo.save(req);
    }

    // Personal bloqueia aluno para impedir novas solicitações + remove conexão existente
    @Transactional
    @PostMapping("/trainer/{trainerId}/students/{studentId}/block")
        public BlockedStudent blockStudent(
            @PathVariable Long trainerId,
            @PathVariable Long studentId,
            @RequestParam(required = false) Long requestId
        ) {
        List<TrainerSlot> preservedSlots = computePreservedSlots(trainerId, studentId);
        final boolean hadActiveConnection = connectionRepo
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

        Set<Long> notifiedRequestIds = new HashSet<>();

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

        // DEPOIS liberar recursos e remover conexão
        releaseApprovedStudentResources(trainerId, studentId, true);
    restorePreservedSlots(trainerId, preservedSlots);

        // Criar ou retornar o bloqueio
        return blockedStudentRepo.findByTrainerIdAndStudentId(trainerId, studentId)
                .orElseGet(() -> {
                    BlockedStudent block = new BlockedStudent();
                    block.setTrainerId(trainerId);
                    block.setStudentId(studentId);
                    return blockedStudentRepo.save(block);
                });
    }

    // Personal remove um aluno aprovado da sua lista e libera os horários do plano
    @Transactional
    @DeleteMapping("/trainer/{trainerId}/students/{studentId}")
        public void removeStudent(
            @PathVariable Long trainerId,
            @PathVariable Long studentId,
            @RequestParam(required = false) Long requestId
        ) {
        List<StudentRequest> pairRequests = requestRepo
                .findByStudentIdAndTrainerIdOrderByCreatedAtDesc(studentId, trainerId);

        if (requestId != null) {
            boolean requestBelongsToPair = pairRequests.stream()
                .anyMatch(req -> requestId.equals(req.getId()));
            if (!requestBelongsToPair) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Solicitação informada não pertence a este aluno/personal"
            );
            }
        }

            List<StudentRequest> approvedRequests = pairRequests.stream()
                    .filter(req -> "APPROVED".equals(req.getStatus()))
                    .toList();

            List<StudentRequest> targetRequests;

            if (!approvedRequests.isEmpty()) {
                // Ao remover aluno, toda solicitação/plano aprovado do par deve receber
                // mensagem de encerramento no próprio chat.
                targetRequests = approvedRequests;
            } else if (requestId != null) {
                StudentRequest targetRequest = pairRequests.stream()
                    .filter(req -> requestId.equals(req.getId()))
                    .findFirst()
                    .orElseThrow();
                targetRequests = List.of(targetRequest);
            } else {
                targetRequests = pairRequests.stream()
                    .filter(req -> "PENDING".equals(req.getStatus()))
                    .toList();
            }

        if (targetRequests.isEmpty() && !pairRequests.isEmpty()) {
            targetRequests = List.of(pairRequests.get(0));
        }

        if (!targetRequests.isEmpty()) {
            for (StudentRequest req : targetRequests) {
                boolean visibilityChanged = false;
                if (Boolean.TRUE.equals(req.getHiddenForTrainer())) {
                    req.setHiddenForTrainer(false);
                    visibilityChanged = true;
                }
                if (Boolean.TRUE.equals(req.getHiddenForStudent())) {
                    req.setHiddenForStudent(false);
                    visibilityChanged = true;
                }
                if (visibilityChanged) {
                    requestRepo.save(req);
                }
                sendStudentRemovedAfterBlockMessage(trainerId, studentId, req);
            }
        } else {
            sendStudentRemovedAfterBlockMessage(trainerId, studentId, null);
        }

        releaseApprovedStudentResources(trainerId, studentId, false);
    }

    // Personal remove bloqueio de aluno
    @DeleteMapping("/trainer/{trainerId}/students/{studentId}/block")
    public void unblockStudent(@PathVariable Long trainerId, @PathVariable Long studentId) {
        blockedStudentService.unblockStudent(trainerId, studentId);
    }

    // Personal lista alunos bloqueados
    @GetMapping("/trainer/{trainerId}/students/blocked")
    public List<BlockedStudent> getBlockedStudents(@PathVariable Long trainerId) {
        return blockedStudentRepo.findByTrainerIdOrderByBlockedAtDesc(trainerId);
    }

    // Aluno apaga uma solicitação
    @DeleteMapping("/{id}")
    public void deleteRequest(@PathVariable Long id) {
        if (!requestRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada");
        }
        requestRepo.deleteById(id);
    }

    @Transactional
    @PatchMapping("/{id}/cancel-by-student")
    public StudentRequest cancelRequestByStudent(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        return cancelRequestByStudentInternal(id, reason);
    }

    @Transactional
    @PostMapping("/{id}/cancel-by-student")
    public StudentRequest cancelRequestByStudentPost(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        return cancelRequestByStudentInternal(id, reason);
    }

    private StudentRequest cancelRequestByStudentInternal(Long id, String reason) {
        StudentRequest req = requestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada"));

        if (!"REJECTED".equals(req.getStatus())) {
            sendStudentCancelledOwnRequestMessage(req, reason);
        }

        releaseRequestSlots(req);

        req.setStatus("REJECTED");
        req.setHiddenForTrainer(false);
        return requestRepo.save(req);
    }

    // Personal remove solicitação apenas da sua própria lista
    @PatchMapping("/{id}/hide-for-trainer")
    public StudentRequest hideRequestForTrainer(@PathVariable Long id) {
        return hideRequestForTrainerInternal(id);
    }

    // Compatibilidade para clientes/rede que não aceitam PATCH
    @PostMapping("/{id}/hide-for-trainer")
    public StudentRequest hideRequestForTrainerPost(@PathVariable Long id) {
        return hideRequestForTrainerInternal(id);
    }

    @DeleteMapping("/{id}/hide-for-trainer")
    public StudentRequest hideRequestForTrainerDelete(@PathVariable Long id) {
        return hideRequestForTrainerInternal(id);
    }

    private StudentRequest hideRequestForTrainerInternal(Long id) {
        StudentRequest req = requestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada"));
        if ("PENDING".equals(req.getStatus())) {
            req.setStatus("REJECTED");
        }
        req.setHiddenForTrainer(true);
        return requestRepo.save(req);
    }

    record SendRequestDto(Long trainerId, Long studentId, String studentName, String trainerName, String dayName, String time, String planType, String daysJson) {}
    record UpdateStatusDto(String status) {}
}
