package fitmatch_api.controller;

import fitmatch_api.model.TrainerSlot;
import fitmatch_api.repository.TrainerSlotRepository;
import fitmatch_api.service.TrainerSlotService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/slots")
@CrossOrigin(origins = "*")
public class SlotController {

    private static final String MANUAL_ONCE_PREFIX = "__ONCE__:";
    private static final String MANUAL_ONCE_SEPARATOR = "|";
    private static final String STATE_UNBLOCK_ONCE = "UNBLOCK_ONCE";
    private static final List<String> WEEK_DAYS = List.of(
            "Segunda",
            "Terça",
            "Quarta",
            "Quinta",
            "Sexta",
            "Sábado",
            "Domingo"
    );

    private final TrainerSlotRepository slotRepo;
    private final TrainerSlotService slotService;

    public SlotController(TrainerSlotRepository slotRepo, TrainerSlotService slotService) {
        this.slotRepo = slotRepo;
        this.slotService = slotService;
    }

    // Retorna todos os slots bloqueados de um personal
    @GetMapping("/trainer/{trainerId}")
    public List<Map<String, Object>> getSlots(@PathVariable Long trainerId) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (TrainerSlot slot : slotRepo.findByTrainerId(trainerId)) {
            String storedDay = slot.getDayName() == null ? "" : slot.getDayName().trim();
            String dayName = decodeDayName(storedDay);
            String dateIso = decodeDateIso(storedDay);
            String repeatMode = dateIso.isEmpty() ? "WEEKLY" : "ONCE";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", slot.getId());
            row.put("trainerId", slot.getTrainerId());
            row.put("dayName", dayName);
            row.put("time", slot.getTime());
            row.put("state", slot.getState());
            row.put("dateIso", dateIso);
            row.put("repeatMode", repeatMode);
            payload.add(row);
        }
        return payload;
    }

    // Personal bloqueia um horário
    @PostMapping("/trainer/{trainerId}/block")
    public Map<String, Object> blockSlot(@PathVariable Long trainerId,
                                  @RequestBody SlotDto dto) {
        if (dto.dayName() == null || dto.dayName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayName é obrigatório");
        }

        String repeatMode = normalizeRepeatMode(dto.repeatMode());
        boolean blockFullDay = Boolean.TRUE.equals(dto.blockFullDay());
        List<String> targetTimes = resolveTargetTimes(dto.time(), blockFullDay);
        if (targetTimes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "time é obrigatório");
        }

        String normalizedDay = dto.dayName().trim();
        String dateIso = normalizeDateIso(dto.dateIso());
        if ("ONCE".equals(repeatMode) && dateIso.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateIso é obrigatório para bloqueio pontual");
        }

        Map<String, Object> lastPayload = null;

        if ("ALL_DAYS".equals(repeatMode)) {
            for (String day : WEEK_DAYS) {
                for (String time : targetTimes) {
                    removeUnblockOnceOverrides(trainerId, day, time);
                    TrainerSlot saved = saveOrUpdateState(trainerId, day, time, "MANUAL");
                    lastPayload = toPayload(saved, day, "", "ALL_DAYS");
                }
            }
        } else if ("ONCE".equals(repeatMode)) {
            String storedDay = buildStoredOnceDay(normalizedDay, dateIso);
            for (String time : targetTimes) {
                TrainerSlot saved = saveOrUpdateState(trainerId, storedDay, time, "MANUAL");
                lastPayload = toPayload(saved, normalizedDay, dateIso, "ONCE");
            }
        } else {
            for (String time : targetTimes) {
                removeUnblockOnceOverrides(trainerId, normalizedDay, time);
                TrainerSlot saved = saveOrUpdateState(trainerId, normalizedDay, time, "MANUAL");
                lastPayload = toPayload(saved, normalizedDay, "", "WEEKLY");
            }
        }

        return lastPayload == null ? Map.of() : lastPayload;
    }

    // Personal desbloqueia um horário
    @DeleteMapping("/trainer/{trainerId}/block")
    public void unblockSlot(@PathVariable Long trainerId,
                             @RequestBody SlotDto dto) {
        if (dto.dayName() == null || dto.dayName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayName é obrigatório");
        }

        String repeatMode = normalizeRepeatMode(dto.repeatMode());
        boolean blockFullDay = Boolean.TRUE.equals(dto.blockFullDay());
        List<String> targetTimes = resolveTargetTimes(dto.time(), blockFullDay);
        if (targetTimes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "time é obrigatório");
        }

        String dayName = dto.dayName().trim();
        String dateIso = normalizeDateIso(dto.dateIso());

        if ("ALL_DAYS".equals(repeatMode)) {
            for (String day : WEEK_DAYS) {
                for (String time : targetTimes) {
                    slotService.unblockSlot(trainerId, day, time);
                }
            }
            return;
        }

        if ("ONCE".equals(repeatMode) || (!dateIso.isEmpty() && "WEEKLY".equals(repeatMode))) {
            if (dateIso.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateIso é obrigatório para desbloqueio pontual");
            }
            String storedDay = buildStoredOnceDay(dayName, dateIso);
            for (String time : targetTimes) {
                var onceSlot = slotRepo.findByTrainerIdAndDayNameAndTime(trainerId, storedDay, time);
                boolean removedOneTimeManual = false;
                if (onceSlot.isPresent()) {
                    String onceState = normalizeState(onceSlot.get().getState());
                    if (!STATE_UNBLOCK_ONCE.equals(onceState)) {
                        slotService.unblockSlot(trainerId, storedDay, time);
                        removedOneTimeManual = true;
                    } else {
                        // Já existe override pontual de desbloqueio para essa data.
                        continue;
                    }
                }

                var weeklySlot = slotRepo.findByTrainerIdAndDayNameAndTime(trainerId, dayName, time);
                if (weeklySlot.isPresent()) {
                    saveOrUpdateState(trainerId, storedDay, time, STATE_UNBLOCK_ONCE);
                    continue;
                }

                // Caso não exista bloqueio semanal, o desbloqueio pontual se resolve
                // apenas removendo o bloqueio MANUAL daquela data.
                if (removedOneTimeManual) {
                    continue;
                }
            }
            return;
        }

        for (String time : targetTimes) {
            slotService.unblockSlot(trainerId, dayName, time);
        }
    }

    private String normalizeRepeatMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return "WEEKLY";
        }
        String value = rawMode.trim().toUpperCase(Locale.ROOT);
        if ("ONCE".equals(value) || "ALL_DAYS".equals(value) || "WEEKLY".equals(value)) {
            return value;
        }
        return "WEEKLY";
    }

    private List<String> resolveTargetTimes(String baseTime, boolean blockFullDay) {
        if (blockFullDay) {
            List<String> times = new ArrayList<>();
            for (int hour = 0; hour < 24; hour++) {
                times.add(String.format(Locale.ROOT, "%02d:00", hour));
            }
            return times;
        }

        if (baseTime == null || baseTime.isBlank()) {
            return List.of();
        }
        return List.of(baseTime.trim());
    }

    private String normalizeDateIso(String rawDateIso) {
        if (rawDateIso == null || rawDateIso.isBlank()) {
            return "";
        }
        String value = rawDateIso.trim();
        String dateText = value.length() >= 10 ? value.substring(0, 10) : value;
        try {
            return LocalDate.parse(dateText).toString();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateIso inválido");
        }
    }

    private String buildStoredOnceDay(String dayName, String dateIso) {
        return MANUAL_ONCE_PREFIX + dateIso + MANUAL_ONCE_SEPARATOR + dayName;
    }

    private boolean isStoredOnceDay(String storedDay) {
        return storedDay != null && storedDay.startsWith(MANUAL_ONCE_PREFIX);
    }

    private String decodeDayName(String storedDay) {
        if (!isStoredOnceDay(storedDay)) {
            return storedDay;
        }
        String payload = storedDay.substring(MANUAL_ONCE_PREFIX.length());
        int sep = payload.indexOf(MANUAL_ONCE_SEPARATOR);
        if (sep < 0 || sep + 1 >= payload.length()) {
            return "";
        }
        return payload.substring(sep + 1);
    }

    private String decodeDateIso(String storedDay) {
        if (!isStoredOnceDay(storedDay)) {
            return "";
        }
        String payload = storedDay.substring(MANUAL_ONCE_PREFIX.length());
        int sep = payload.indexOf(MANUAL_ONCE_SEPARATOR);
        if (sep <= 0) {
            return "";
        }
        return payload.substring(0, sep);
    }

    private String normalizeState(String rawState) {
        if (rawState == null) {
            return "";
        }
        return rawState.trim().toUpperCase(Locale.ROOT);
    }

    private void removeUnblockOnceOverrides(Long trainerId, String dayName, String time) {
        String safeDay = dayName == null ? "" : dayName.trim();
        String safeTime = time == null ? "" : time.trim();
        if (safeDay.isEmpty() || safeTime.isEmpty()) {
            return;
        }

        for (TrainerSlot slot : slotRepo.findByTrainerId(trainerId)) {
            if (!STATE_UNBLOCK_ONCE.equals(normalizeState(slot.getState()))) {
                continue;
            }
            String slotTime = slot.getTime() == null ? "" : slot.getTime().trim();
            if (!safeTime.equals(slotTime)) {
                continue;
            }
            if (!isStoredOnceDay(slot.getDayName())) {
                continue;
            }
            String onceDayName = decodeDayName(slot.getDayName()).trim();
            if (!onceDayName.equalsIgnoreCase(safeDay)) {
                continue;
            }
            slotRepo.delete(slot);
        }
    }

    private TrainerSlot saveOrUpdateState(Long trainerId, String dayName, String time, String state) {
        return slotRepo
                .findByTrainerIdAndDayNameAndTime(trainerId, dayName, time)
                .map(existing -> {
                    if (!state.equalsIgnoreCase(existing.getState())) {
                        existing.setState(state);
                        return slotRepo.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    TrainerSlot slot = new TrainerSlot();
                    slot.setTrainerId(trainerId);
                    slot.setDayName(dayName);
                    slot.setTime(time);
                    slot.setState(state);
                    return slotRepo.save(slot);
                });
    }

    private Map<String, Object> toPayload(TrainerSlot slot, String dayName, String dateIso, String repeatMode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", slot.getId());
        payload.put("trainerId", slot.getTrainerId());
        payload.put("dayName", dayName);
        payload.put("time", slot.getTime());
        payload.put("state", slot.getState());
        payload.put("dateIso", dateIso);
        payload.put("repeatMode", repeatMode);
        return payload;
    }

    record SlotDto(String dayName, String time, String dateIso, String repeatMode, Boolean blockFullDay) {}
}
