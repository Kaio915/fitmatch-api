package fitmatch_api.controller;

import fitmatch_api.model.StudentWorkoutPlan;
import fitmatch_api.model.WorkoutCustomExercise;
import fitmatch_api.model.WorkoutFavorite;
import fitmatch_api.repository.StudentRequestRepository;
import fitmatch_api.repository.StudentWorkoutPlanRepository;
import fitmatch_api.repository.WorkoutCustomExerciseRepository;
import fitmatch_api.repository.WorkoutFavoriteRepository;
import fitmatch_api.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/workouts")
public class WorkoutController {

    private static final String PLAN_SLOT_SEPARATOR = "||";

    private final StudentWorkoutPlanRepository workoutPlanRepo;
    private final WorkoutFavoriteRepository favoriteRepo;
    private final WorkoutCustomExerciseRepository customExerciseRepo;
    private final StudentRequestRepository requestRepo;

    private static final Pattern EXERCISE_PATTERN_NAME_CATEGORY = Pattern.compile(
            "\\{[^}]*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^}]*\\\"category\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"[^}]*\\}"
    );
    private static final Pattern EXERCISE_PATTERN_CATEGORY_NAME = Pattern.compile(
            "\\{[^}]*\\\"category\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"[^}]*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^}]*\\}"
    );
    private static final Pattern EXERCISE_PATTERN_NAME_ONLY = Pattern.compile(
            "\\{[^}]*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^}]*\\}"
    );

    private static final Map<String, Integer> DAY_ORDER = Map.of(
            "Segunda", 1,
            "Terça", 2,
            "Quarta", 3,
            "Quinta", 4,
            "Sexta", 5,
            "Sábado", 6,
            "Domingo", 7
    );

    private static final List<Map<String, String>> CATALOG = List.of(
            Map.of("name", "Supino Máquina", "category", "Máquina"),
            Map.of("name", "Puxada Frente", "category", "Máquina"),
            Map.of("name", "Remada Baixa", "category", "Máquina"),
            Map.of("name", "Leg Press", "category", "Máquina"),
            Map.of("name", "Cadeira Extensora", "category", "Máquina"),
            Map.of("name", "Cadeira Flexora", "category", "Máquina"),
            Map.of("name", "Panturrilha Máquina", "category", "Máquina"),
            Map.of("name", "Desenvolvimento Máquina", "category", "Máquina"),
            Map.of("name", "Rosca Direta Halteres", "category", "Halteres"),
            Map.of("name", "Rosca Martelo", "category", "Halteres"),
            Map.of("name", "Tríceps Francês", "category", "Halteres"),
            Map.of("name", "Elevação Lateral", "category", "Halteres"),
            Map.of("name", "Elevação Frontal", "category", "Halteres"),
            Map.of("name", "Agachamento Goblet", "category", "Halteres"),
            Map.of("name", "Passada com Halteres", "category", "Halteres"),
            Map.of("name", "Stiff com Halteres", "category", "Halteres"),
            Map.of("name", "Esteira", "category", "Aeróbico"),
            Map.of("name", "Bicicleta Ergométrica", "category", "Aeróbico"),
            Map.of("name", "Elíptico", "category", "Aeróbico"),
            Map.of("name", "Escada", "category", "Aeróbico"),
            Map.of("name", "Remo Indoor", "category", "Aeróbico"),
            Map.of("name", "Polichinelo", "category", "Aeróbico"),
            Map.of("name", "Burpee", "category", "Aeróbico"),
            Map.of("name", "Corrida Estacionária", "category", "Aeróbico"),
            Map.of("name", "Flexão de Braço", "category", "Peso Corporal"),
            Map.of("name", "Agachamento Livre", "category", "Peso Corporal"),
            Map.of("name", "Afundo", "category", "Peso Corporal"),
            Map.of("name", "Prancha", "category", "Core"),
            Map.of("name", "Prancha Lateral", "category", "Core"),
            Map.of("name", "Abdominal Reto", "category", "Core"),
            Map.of("name", "Abdominal Bicicleta", "category", "Core"),
            Map.of("name", "Mountain Climber", "category", "Funcional"),
            Map.of("name", "Kettlebell Swing", "category", "Funcional"),
            Map.of("name", "Pular Corda", "category", "Funcional"),
            Map.of("name", "Mobilidade de Quadril", "category", "Mobilidade"),
            Map.of("name", "Alongamento Posterior", "category", "Mobilidade")
    );

    public WorkoutController(
            StudentWorkoutPlanRepository workoutPlanRepo,
            WorkoutFavoriteRepository favoriteRepo,
            WorkoutCustomExerciseRepository customExerciseRepo,
            StudentRequestRepository requestRepo
    ) {
        this.workoutPlanRepo = workoutPlanRepo;
        this.favoriteRepo = favoriteRepo;
        this.customExerciseRepo = customExerciseRepo;
        this.requestRepo = requestRepo;
    }

    @GetMapping("/catalog")
    public List<Map<String, String>> getCatalog(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) {
            return CATALOG;
        }

        String query = normalizeText(q);
        return CATALOG.stream()
                .filter(item -> normalizeText(item.getOrDefault("name", "")).contains(query)
                        || normalizeText(item.getOrDefault("category", "")).contains(query))
                .toList();
    }

            @GetMapping("/trainer/{trainerId}/custom-exercises")
            public List<Map<String, Object>> getCustomExercises(@PathVariable Long trainerId) {
            AuthContext.requireSelfOrAdmin(trainerId);
            return customExerciseRepo.findByTrainerIdOrderByUpdatedAtDesc(trainerId)
                .stream()
                .map(this::toCustomExercisePayload)
                .toList();
            }

            @PostMapping("/trainer/{trainerId}/custom-exercises")
            public Map<String, Object> createCustomExercise(
                @PathVariable Long trainerId,
                @RequestBody CustomExerciseUpsertDto dto
            ) {
            AuthContext.requireSelfOrAdmin(trainerId);
            if (dto == null || dto.name() == null || dto.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do treino é obrigatório");
            }

            String name = dto.name().trim();
            String category = dto.category() == null || dto.category().isBlank()
                ? "Personalizado"
                : dto.category().trim();

            if (customExerciseRepo.existsByTrainerIdAndNameIgnoreCase(trainerId, name)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Treino personalizado já cadastrado");
            }

            WorkoutCustomExercise custom = new WorkoutCustomExercise();
            custom.setTrainerId(trainerId);
            custom.setName(name);
            custom.setCategory(category);

            return toCustomExercisePayload(customExerciseRepo.save(custom));
            }

            @PutMapping("/trainer/{trainerId}/custom-exercises/{exerciseId}")
            public Map<String, Object> updateCustomExercise(
                @PathVariable Long trainerId,
                @PathVariable Long exerciseId,
                @RequestBody CustomExerciseUpsertDto dto
            ) {
            AuthContext.requireSelfOrAdmin(trainerId);
            if (dto == null || dto.name() == null || dto.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do treino é obrigatório");
            }

            WorkoutCustomExercise custom = customExerciseRepo.findByIdAndTrainerId(exerciseId, trainerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Treino personalizado não encontrado"));

            String newName = dto.name().trim();
            String oldName = custom.getName() == null ? "" : custom.getName().trim();
            if (!oldName.equalsIgnoreCase(newName)
                && customExerciseRepo.existsByTrainerIdAndNameIgnoreCase(trainerId, newName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Treino personalizado já cadastrado");
            }

            custom.setName(newName);
            custom.setCategory(dto.category() == null || dto.category().isBlank()
                ? "Personalizado"
                : dto.category().trim());

            return toCustomExercisePayload(customExerciseRepo.save(custom));
            }

            @DeleteMapping("/trainer/{trainerId}/custom-exercises/{exerciseId}")
            public void deleteCustomExercise(
                @PathVariable Long trainerId,
                @PathVariable Long exerciseId
            ) {
            AuthContext.requireSelfOrAdmin(trainerId);
            WorkoutCustomExercise custom = customExerciseRepo.findByIdAndTrainerId(exerciseId, trainerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Treino personalizado não encontrado"));
            customExerciseRepo.delete(custom);
            }

    @GetMapping("/trainer/{trainerId}/favorites")
    public List<Map<String, Object>> getFavorites(@PathVariable Long trainerId) {
        AuthContext.requireSelfOrAdmin(trainerId);
        return favoriteRepo.findByTrainerIdOrderByUpdatedAtDesc(trainerId)
                .stream()
                .map(this::toFavoritePayload)
                .toList();
    }

    @PostMapping("/trainer/{trainerId}/favorites")
    public Map<String, Object> createFavorite(
            @PathVariable Long trainerId,
            @RequestBody FavoriteUpsertDto dto
    ) {
        AuthContext.requireSelfOrAdmin(trainerId);
        validateFavoriteDto(dto);
        List<Map<String, String>> exercises = sanitizeExercises(dto.exercises());
        validateFavoriteUniqueness(trainerId, exercises, null);

        WorkoutFavorite favorite = new WorkoutFavorite();
        favorite.setTrainerId(trainerId);
        favorite.setName(dto.name().trim());
        favorite.setExercisesJson(writeExercisesJson(exercises));

        return toFavoritePayload(favoriteRepo.save(favorite));
    }

    @PutMapping("/trainer/{trainerId}/favorites/{favoriteId}")
    public Map<String, Object> updateFavorite(
            @PathVariable Long trainerId,
            @PathVariable Long favoriteId,
            @RequestBody FavoriteUpsertDto dto
    ) {
        AuthContext.requireSelfOrAdmin(trainerId);
        validateFavoriteDto(dto);
        List<Map<String, String>> exercises = sanitizeExercises(dto.exercises());
        validateFavoriteUniqueness(trainerId, exercises, favoriteId);

        WorkoutFavorite favorite = favoriteRepo.findByIdAndTrainerId(favoriteId, trainerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorito não encontrado"));

        favorite.setName(dto.name().trim());
        favorite.setExercisesJson(writeExercisesJson(exercises));

        return toFavoritePayload(favoriteRepo.save(favorite));
    }

    @DeleteMapping("/trainer/{trainerId}/favorites/{favoriteId}")
    public void deleteFavorite(
            @PathVariable Long trainerId,
            @PathVariable Long favoriteId
    ) {
        AuthContext.requireSelfOrAdmin(trainerId);
        WorkoutFavorite favorite = favoriteRepo.findByIdAndTrainerId(favoriteId, trainerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorito não encontrado"));
        favoriteRepo.delete(favorite);
    }

        @PostMapping("/trainer/{trainerId}/favorites/{favoriteId}/clone")
        public Map<String, Object> cloneFavorite(
            @PathVariable Long trainerId,
            @PathVariable Long favoriteId,
            @RequestBody(required = false) CloneFavoriteDto dto
        ) {
        AuthContext.requireSelfOrAdmin(trainerId);
        WorkoutFavorite source = favoriteRepo.findByIdAndTrainerId(favoriteId, trainerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorito não encontrado"));

        String cloneName = dto == null || dto.name() == null || dto.name().isBlank()
            ? (source.getName() == null || source.getName().isBlank() ? "Favorito" : source.getName().trim()) + " (copia)"
            : dto.name().trim();

        if (cloneName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do clone é obrigatório");
        }

        WorkoutFavorite clone = new WorkoutFavorite();
        clone.setTrainerId(trainerId);
        clone.setName(cloneName);
        clone.setExercisesJson(source.getExercisesJson() == null ? "[]" : source.getExercisesJson());

        return toFavoritePayload(favoriteRepo.save(clone));
        }

    @GetMapping("/trainer/{trainerId}/students/{studentId}/plans")
    public List<Map<String, Object>> getStudentPlansByTrainer(
            @PathVariable Long trainerId,
            @PathVariable Long studentId
    ) {
        AuthContext.requireSelfOrAdmin(trainerId);
        ensureApprovedPair(trainerId, studentId);
        return getSortedStudentPlans(trainerId, studentId)
                .stream()
                .map(this::toPlanPayload)
                .toList();
    }

    @PostMapping("/trainer/{trainerId}/students/{studentId}/plans")
    public Map<String, Object> upsertStudentPlan(
            @PathVariable Long trainerId,
            @PathVariable Long studentId,
            @RequestBody DayPlanUpsertDto dto
    ) {
        AuthContext.requireSelfOrAdmin(trainerId);
        ensureApprovedPair(trainerId, studentId);
        String dayName = normalizeAndValidateDay(dto.dayName());
        String time = normalizeAndValidateTime(dto.time());
        String storedDay = buildStoredDayValue(dayName, time);
        List<Map<String, String>> exercises = sanitizeExercises(dto.exercises());

        StudentWorkoutPlan plan = workoutPlanRepo
            .findByTrainerIdAndStudentIdAndDayName(trainerId, studentId, storedDay)
                .orElseGet(StudentWorkoutPlan::new);

        plan.setTrainerId(trainerId);
        plan.setStudentId(studentId);
        plan.setDayName(storedDay);
        plan.setExercisesJson(writeExercisesJson(exercises));

        return toPlanPayload(workoutPlanRepo.save(plan));
    }

    @PutMapping("/trainer/{trainerId}/students/{studentId}/plans/{planId}")
    public Map<String, Object> updateStudentPlan(
            @PathVariable Long trainerId,
            @PathVariable Long studentId,
            @PathVariable Long planId,
            @RequestBody DayPlanUpsertDto dto
    ) {
        AuthContext.requireSelfOrAdmin(trainerId);
        ensureApprovedPair(trainerId, studentId);
        String dayName = normalizeAndValidateDay(dto.dayName());
        String time = normalizeAndValidateTime(dto.time());
        String storedDay = buildStoredDayValue(dayName, time);
        List<Map<String, String>> exercises = sanitizeExercises(dto.exercises());

        StudentWorkoutPlan plan = workoutPlanRepo.findByIdAndTrainerIdAndStudentId(planId, trainerId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Treino do aluno não encontrado"));

        Optional<StudentWorkoutPlan> sameSlot = workoutPlanRepo
            .findByTrainerIdAndStudentIdAndDayName(trainerId, studentId, storedDay);
        if (sameSlot.isPresent() && !Objects.equals(sameSlot.get().getId(), plan.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe treino cadastrado para esse dia e horário");
        }

        plan.setDayName(storedDay);
        plan.setExercisesJson(writeExercisesJson(exercises));

        return toPlanPayload(workoutPlanRepo.save(plan));
    }

    @DeleteMapping("/trainer/{trainerId}/students/{studentId}/plans/{planId}")
    public void deleteStudentPlan(
            @PathVariable Long trainerId,
            @PathVariable Long studentId,
            @PathVariable Long planId
    ) {
        AuthContext.requireSelfOrAdmin(trainerId);
        ensureApprovedPair(trainerId, studentId);
        StudentWorkoutPlan plan = workoutPlanRepo.findByIdAndTrainerIdAndStudentId(planId, trainerId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Treino do aluno não encontrado"));
        workoutPlanRepo.delete(plan);
    }

    @GetMapping("/student/{studentId}/trainer/{trainerId}/plans")
    public List<Map<String, Object>> getStudentPlansByStudent(
            @PathVariable Long studentId,
            @PathVariable Long trainerId
    ) {
        AuthContext.requireSelfOrAdminFromAny(studentId, trainerId);
        ensureApprovedPair(trainerId, studentId);
        return getSortedStudentPlans(trainerId, studentId)
                .stream()
                .map(this::toPlanPayload)
                .toList();
    }

    private void ensureApprovedPair(Long trainerId, Long studentId) {
        if (trainerId == null || studentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trainerId e studentId são obrigatórios");
        }

        boolean hasApproved = !requestRepo
                .findByStudentIdAndTrainerIdAndStatusOrderByCreatedAtDesc(studentId, trainerId, "APPROVED")
                .isEmpty();

        if (!hasApproved) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aluno não pertence aos alunos ativos deste personal");
        }
    }

    private List<StudentWorkoutPlan> getSortedStudentPlans(Long trainerId, Long studentId) {
        return workoutPlanRepo.findByTrainerIdAndStudentId(trainerId, studentId)
                .stream()
                .sorted((a, b) -> {
                    String aDay = extractDisplayDayName(a.getDayName());
                    String bDay = extractDisplayDayName(b.getDayName());
                    String aTime = extractDisplayTime(a.getDayName());
                    String bTime = extractDisplayTime(b.getDayName());

                    int oa = DAY_ORDER.getOrDefault(aDay, 99);
                    int ob = DAY_ORDER.getOrDefault(bDay, 99);
                    if (oa != ob) return Integer.compare(oa, ob);

                    int byTime = compareTime(aTime, bTime);
                    if (byTime != 0) return byTime;

                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                    if (a.getUpdatedAt() == null) return 1;
                    if (b.getUpdatedAt() == null) return -1;
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                })
                .collect(Collectors.toList());
    }

    private void validateFavoriteDto(FavoriteUpsertDto dto) {
        if (dto == null || dto.name() == null || dto.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do favorito é obrigatório");
        }
    }

    private void validateFavoriteUniqueness(
            Long trainerId,
            List<Map<String, String>> exercises,
            Long ignoreFavoriteId
    ) {
        String targetSignature = exercisesSignature(exercises);
        if (targetSignature.isBlank()) {
            return;
        }

        for (WorkoutFavorite favorite : favoriteRepo.findByTrainerIdOrderByUpdatedAtDesc(trainerId)) {
            if (ignoreFavoriteId != null && Objects.equals(favorite.getId(), ignoreFavoriteId)) {
                continue;
            }

            String currentSignature = exercisesSignature(readExercisesJson(favorite.getExercisesJson()));
            if (targetSignature.equals(currentSignature)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Esse treino já foi salvo como favorito");
            }
        }
    }

    private String exercisesSignature(List<Map<String, String>> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return "";
        }

        Set<String> normalized = new HashSet<>();
        for (Map<String, String> exercise : exercises) {
            if (exercise == null) continue;
            String name = exercise.getOrDefault("name", "").trim();
            String category = exercise.getOrDefault("category", "Outros").trim();
            if (name.isEmpty()) continue;
            if (category.isEmpty()) category = "Outros";
            normalized.add(normalizeText(name) + "|" + normalizeText(category));
        }

        List<String> sorted = new ArrayList<>(normalized);
        Collections.sort(sorted);
        return String.join("||", sorted);
    }

    private String normalizeAndValidateDay(String rawDay) {
        if (rawDay == null || rawDay.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dia do treino é obrigatório");
        }

        String normalized = normalizeText(rawDay);
        if (normalized.startsWith("seg")) return "Segunda";
        if (normalized.startsWith("ter")) return "Terça";
        if (normalized.startsWith("qua")) return "Quarta";
        if (normalized.startsWith("qui")) return "Quinta";
        if (normalized.startsWith("sex")) return "Sexta";
        if (normalized.startsWith("sab")) return "Sábado";
        if (normalized.startsWith("dom")) return "Domingo";

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dia inválido para treino");
    }

    private String normalizeAndValidateTime(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) {
            return "";
        }

        String value = rawTime.trim();
        Matcher match = Pattern.compile("^(\\d{1,2}):(\\d{2})").matcher(value);
        if (!match.find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horário inválido para treino");
        }

        int hour = Integer.parseInt(match.group(1));
        int minute = Integer.parseInt(match.group(2));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horário inválido para treino");
        }

        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private String buildStoredDayValue(String dayName, String time) {
        if (time == null || time.isBlank()) {
            return dayName;
        }
        return dayName + PLAN_SLOT_SEPARATOR + time;
    }

    private String extractDisplayDayName(String storedDay) {
        if (storedDay == null || storedDay.isBlank()) {
            return "";
        }
        int sep = storedDay.indexOf(PLAN_SLOT_SEPARATOR);
        if (sep <= 0) {
            return storedDay.trim();
        }
        return storedDay.substring(0, sep).trim();
    }

    private String extractDisplayTime(String storedDay) {
        if (storedDay == null || storedDay.isBlank()) {
            return "";
        }
        int sep = storedDay.indexOf(PLAN_SLOT_SEPARATOR);
        if (sep < 0 || sep + PLAN_SLOT_SEPARATOR.length() >= storedDay.length()) {
            return "";
        }
        String raw = storedDay.substring(sep + PLAN_SLOT_SEPARATOR.length()).trim();
        Matcher match = Pattern.compile("^(\\d{1,2}):(\\d{2})").matcher(raw);
        if (!match.find()) {
            return "";
        }
        int hour = Integer.parseInt(match.group(1));
        int minute = Integer.parseInt(match.group(2));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "";
        }
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private int compareTime(String a, String b) {
        String ta = a == null ? "" : a.trim();
        String tb = b == null ? "" : b.trim();
        if (ta.isEmpty() && tb.isEmpty()) return 0;
        if (ta.isEmpty()) return 1;
        if (tb.isEmpty()) return -1;
        return ta.compareTo(tb);
    }

    private List<Map<String, String>> sanitizeExercises(List<ExerciseDto> rawExercises) {
        if (rawExercises == null || rawExercises.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione pelo menos um treino");
        }

        List<Map<String, String>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ExerciseDto exercise : rawExercises) {
            if (exercise == null) continue;
            String name = exercise.name() == null ? "" : exercise.name().trim();
            String category = exercise.category() == null ? "Outros" : exercise.category().trim();
            if (name.isEmpty()) continue;
            if (category.isEmpty()) category = "Outros";

            String key = normalizeText(name) + "|" + normalizeText(category);
            if (!seen.add(key)) {
                continue;
            }

            result.add(Map.of(
                    "name", name,
                    "category", category
            ));
        }

        if (result.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione pelo menos um treino válido");
        }

        return result;
    }

    private String writeExercisesJson(List<Map<String, String>> exercises) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < exercises.size(); i++) {
            Map<String, String> exercise = exercises.get(i);
            String name = escapeJson(exercise.getOrDefault("name", ""));
            String category = escapeJson(exercise.getOrDefault("category", "Outros"));
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"name\":\"")
                    .append(name)
                    .append("\",\"category\":\"")
                    .append(category)
                    .append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    private List<Map<String, String>> readExercisesJson(String exercisesJson) {
        if (exercisesJson == null || exercisesJson.isBlank()) {
            return List.of();
        }

        List<Map<String, String>> parsed = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Matcher nameCategoryMatcher = EXERCISE_PATTERN_NAME_CATEGORY.matcher(exercisesJson);
        while (nameCategoryMatcher.find()) {
            String name = nameCategoryMatcher.group(1).trim();
            String category = nameCategoryMatcher.group(2).trim();
            if (name.isEmpty()) continue;
            if (category.isEmpty()) category = "Outros";
            String key = normalizeText(name) + "|" + normalizeText(category);
            if (!seen.add(key)) continue;
            parsed.add(Map.of("name", name, "category", category));
        }

        Matcher categoryNameMatcher = EXERCISE_PATTERN_CATEGORY_NAME.matcher(exercisesJson);
        while (categoryNameMatcher.find()) {
            String category = categoryNameMatcher.group(1).trim();
            String name = categoryNameMatcher.group(2).trim();
            if (name.isEmpty()) continue;
            if (category.isEmpty()) category = "Outros";
            String key = normalizeText(name) + "|" + normalizeText(category);
            if (!seen.add(key)) continue;
            parsed.add(Map.of("name", name, "category", category));
        }

        if (parsed.isEmpty()) {
            Matcher nameOnlyMatcher = EXERCISE_PATTERN_NAME_ONLY.matcher(exercisesJson);
            while (nameOnlyMatcher.find()) {
                String name = nameOnlyMatcher.group(1).trim();
                if (name.isEmpty()) continue;
                String key = normalizeText(name) + "|outros";
                if (!seen.add(key)) continue;
                parsed.add(Map.of("name", name, "category", "Outros"));
            }
        }

        return parsed;
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private Map<String, Object> toFavoritePayload(WorkoutFavorite favorite) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", favorite.getId());
        payload.put("trainerId", favorite.getTrainerId());
        payload.put("name", favorite.getName());
        payload.put("exercises", readExercisesJson(favorite.getExercisesJson()));
        payload.put("createdAt", favorite.getCreatedAt());
        payload.put("updatedAt", favorite.getUpdatedAt());
        return payload;
    }

    private Map<String, Object> toPlanPayload(StudentWorkoutPlan plan) {
        String displayDayName = extractDisplayDayName(plan.getDayName());
        String displayTime = extractDisplayTime(plan.getDayName());
        if (displayDayName == null || displayDayName.isBlank()) {
            displayDayName = plan.getDayName();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", plan.getId());
        payload.put("trainerId", plan.getTrainerId());
        payload.put("studentId", plan.getStudentId());
        payload.put("dayName", displayDayName);
        payload.put("time", displayTime);
        payload.put("exercises", readExercisesJson(plan.getExercisesJson()));
        payload.put("createdAt", plan.getCreatedAt());
        payload.put("updatedAt", plan.getUpdatedAt());
        return payload;
    }

    private Map<String, Object> toCustomExercisePayload(WorkoutCustomExercise custom) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", custom.getId());
        payload.put("trainerId", custom.getTrainerId());
        payload.put("name", custom.getName());
        payload.put("category", custom.getCategory());
        payload.put("createdAt", custom.getCreatedAt());
        payload.put("updatedAt", custom.getUpdatedAt());
        return payload;
    }

    private String normalizeText(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized;
    }

    record ExerciseDto(String name, String category) {
    }

    record FavoriteUpsertDto(String name, List<ExerciseDto> exercises) {
    }

    record CloneFavoriteDto(String name) {
    }

    record DayPlanUpsertDto(String dayName, String time, List<ExerciseDto> exercises) {
    }

    record CustomExerciseUpsertDto(String name, String category) {
    }
}
