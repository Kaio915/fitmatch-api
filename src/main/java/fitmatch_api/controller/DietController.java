package fitmatch_api.controller;

import fitmatch_api.model.DietEntry;
import fitmatch_api.model.DietFood;
import fitmatch_api.model.DietGoal;
import fitmatch_api.model.DietSavedMeal;
import fitmatch_api.model.DietSavedMealItem;
import fitmatch_api.repository.DietEntryRepository;
import fitmatch_api.repository.DietFoodRepository;
import fitmatch_api.repository.DietGoalRepository;
import fitmatch_api.repository.DietSavedMealRepository;
import fitmatch_api.repository.UserRepository;
import fitmatch_api.service.EdamamFoodService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/diet")
public class DietController {

    private static final List<String> MEAL_ORDER = List.of(
            "Café da Manhã",
            "Lanche da Manhã",
            "Almoço",
            "Lanche da Tarde",
            "Jantar",
            "Ceia"
    );

    private final DietFoodRepository foodRepo;
    private final DietEntryRepository entryRepo;
    private final DietGoalRepository goalRepo;
    private final DietSavedMealRepository savedMealRepo;
    private final UserRepository userRepo;
    private final EdamamFoodService edamamFoodService;

    public DietController(
            DietFoodRepository foodRepo,
            DietEntryRepository entryRepo,
            DietGoalRepository goalRepo,
            DietSavedMealRepository savedMealRepo,
            UserRepository userRepo,
            EdamamFoodService edamamFoodService
    ) {
        this.foodRepo = foodRepo;
        this.entryRepo = entryRepo;
        this.goalRepo = goalRepo;
        this.savedMealRepo = savedMealRepo;
        this.userRepo = userRepo;
        this.edamamFoodService = edamamFoodService;
    }

    @GetMapping("/{userId}/edamam/search")
    public List<Map<String, Object>> searchEdamamFoods(
            @PathVariable Long userId,
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "12") Integer limit
    ) {
        ensureUserExists(userId);
        return edamamFoodService.searchFoods(userId, query, limit);
    }

    @GetMapping("/{userId}/foods")
    public List<Map<String, Object>> getFoods(@PathVariable Long userId) {
        ensureUserExists(userId);
        return foodRepo.findByUserIdOrderByNameAsc(userId)
                .stream()
                .map(this::toFoodPayload)
                .toList();
    }

    @PostMapping("/{userId}/foods")
    public Map<String, Object> createFood(
            @PathVariable Long userId,
            @RequestBody FoodUpsertDto dto
    ) {
        ensureUserExists(userId);
        validateFoodDto(dto);

        if (foodRepo.existsByUserIdAndNameIgnoreCase(userId, dto.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já cadastrou esse alimento");
        }

        DietFood food = new DietFood();
        food.setUserId(userId);
        food.setName(dto.name().trim());
        food.setCaloriesPer100g(normalizePositive(dto.caloriesPer100g(), "Calorias"));
        food.setProteinPer100g(normalizeNonNegative(dto.proteinPer100g(), "Proteína"));
        food.setCarbsPer100g(normalizeNonNegative(dto.carbsPer100g(), "Carboidratos"));
        food.setFatPer100g(normalizeNonNegative(dto.fatPer100g(), "Gordura"));
        food.setFavorite(Boolean.TRUE.equals(dto.favorite()));

        return toFoodPayload(foodRepo.save(food));
    }

    @PutMapping("/{userId}/foods/{foodId}")
    public Map<String, Object> updateFood(
            @PathVariable Long userId,
            @PathVariable Long foodId,
            @RequestBody FoodUpsertDto dto
    ) {
        ensureUserExists(userId);
        validateFoodDto(dto);

        DietFood food = foodRepo.findByIdAndUserId(foodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimento não encontrado"));

        String nextName = dto.name().trim();
        String prevName = food.getName() == null ? "" : food.getName().trim();
        if (!prevName.equalsIgnoreCase(nextName)
                && foodRepo.existsByUserIdAndNameIgnoreCase(userId, nextName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe alimento com esse nome");
        }

        food.setName(nextName);
        food.setCaloriesPer100g(normalizePositive(dto.caloriesPer100g(), "Calorias"));
        food.setProteinPer100g(normalizeNonNegative(dto.proteinPer100g(), "Proteína"));
        food.setCarbsPer100g(normalizeNonNegative(dto.carbsPer100g(), "Carboidratos"));
        food.setFatPer100g(normalizeNonNegative(dto.fatPer100g(), "Gordura"));
        food.setFavorite(Boolean.TRUE.equals(dto.favorite()));

        return toFoodPayload(foodRepo.save(food));
    }

    @PatchMapping("/{userId}/foods/{foodId}/favorite")
    public Map<String, Object> toggleFavorite(
            @PathVariable Long userId,
            @PathVariable Long foodId,
            @RequestBody FavoriteToggleDto dto
    ) {
        ensureUserExists(userId);

        DietFood food = foodRepo.findByIdAndUserId(foodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimento não encontrado"));

        food.setFavorite(Boolean.TRUE.equals(dto.favorite()));
        return toFoodPayload(foodRepo.save(food));
    }

    @DeleteMapping("/{userId}/foods/{foodId}")
    public void deleteFood(
            @PathVariable Long userId,
            @PathVariable Long foodId
    ) {
        ensureUserExists(userId);

        DietFood food = foodRepo.findByIdAndUserId(foodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimento não encontrado"));

        if (entryRepo.existsByUserIdAndFoodId(userId, foodId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Esse alimento já possui registros no diário. Remova os registros antes de excluir."
            );
        }

        foodRepo.delete(food);
    }

    @GetMapping("/{userId}/goals")
    public Map<String, Object> getGoals(@PathVariable Long userId) {
        ensureUserExists(userId);

        DietGoal goal = goalRepo.findByUserId(userId).orElse(null);
        double basal = goal == null || goal.getBasalKcal() == null ? 0.0 : goal.getBasalKcal();
        double target = goal == null || goal.getTargetKcal() == null ? 0.0 : goal.getTargetKcal();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("basalKcal", round1(basal));
        payload.put("targetKcal", round1(target));
        return payload;
    }

    @PutMapping("/{userId}/goals")
    public Map<String, Object> saveGoals(
            @PathVariable Long userId,
            @RequestBody GoalUpsertDto dto
    ) {
        ensureUserExists(userId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados da meta são obrigatórios");
        }

        double basal = normalizeNonNegative(dto.basalKcal(), "TMB");
        double target = normalizeNonNegative(dto.targetKcal(), "Meta diária");

        DietGoal goal = goalRepo.findByUserId(userId).orElseGet(DietGoal::new);
        goal.setUserId(userId);
        goal.setBasalKcal(basal);
        goal.setTargetKcal(target);
        goalRepo.save(goal);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("basalKcal", round1(basal));
        payload.put("targetKcal", round1(target));
        return payload;
    }

    @GetMapping("/{userId}/entries")
    public Map<String, Object> getEntriesByDate(
            @PathVariable Long userId,
            @RequestParam(required = false) String date
    ) {
        ensureUserExists(userId);

        LocalDate targetDate = parseDate(date);
        List<DietFood> foods = foodRepo.findByUserIdOrderByNameAsc(userId);
        Map<Long, DietFood> foodById = new HashMap<>();
        for (DietFood food : foods) {
            foodById.put(food.getId(), food);
        }

        List<DietEntry> entries = entryRepo.findByUserIdAndEntryDateOrderByCreatedAtAsc(userId, targetDate);

        double totalKcal = 0;
        double totalProtein = 0;
        double totalCarbs = 0;
        double totalFat = 0;

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (String mealType : MEAL_ORDER) {
            grouped.put(mealType, new ArrayList<>());
        }

        for (DietEntry entry : entries) {
            DietFood food = foodById.get(entry.getFoodId());
            if (food == null) {
                continue;
            }

            double factor = safe(entry.getQuantityGrams()) / 100.0;
            double kcal = safe(food.getCaloriesPer100g()) * factor;
            double protein = safe(food.getProteinPer100g()) * factor;
            double carbs = safe(food.getCarbsPer100g()) * factor;
            double fat = safe(food.getFatPer100g()) * factor;

            totalKcal += kcal;
            totalProtein += protein;
            totalCarbs += carbs;
            totalFat += fat;

            String mealType = normalizeMealType(entry.getMealType());
            grouped.putIfAbsent(mealType, new ArrayList<>());
            grouped.get(mealType).add(toEntryPayload(entry, food, kcal, protein, carbs, fat));
        }

        List<Map<String, Object>> meals = new ArrayList<>();
        grouped.forEach((mealType, mealEntries) -> {
            if (mealEntries.isEmpty()) return;

            double mealKcal = 0;
            double mealProtein = 0;
            double mealCarbs = 0;
            double mealFat = 0;

            for (Map<String, Object> e : mealEntries) {
                mealKcal += safeNumber(e.get("calories"));
                mealProtein += safeNumber(e.get("protein"));
                mealCarbs += safeNumber(e.get("carbs"));
                mealFat += safeNumber(e.get("fat"));
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mealType", mealType);
            row.put("totalCalories", round1(mealKcal));
            row.put("totalProtein", round1(mealProtein));
            row.put("totalCarbs", round1(mealCarbs));
            row.put("totalFat", round1(mealFat));
            row.put("entries", mealEntries);
            meals.add(row);
        });

        DietGoal goal = goalRepo.findByUserId(userId).orElse(null);
        double targetKcal = goal == null ? 0 : safe(goal.getTargetKcal());
        double basalKcal = goal == null ? 0 : safe(goal.getBasalKcal());

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("consumedKcal", round1(totalKcal));
        totals.put("protein", round1(totalProtein));
        totals.put("carbs", round1(totalCarbs));
        totals.put("fat", round1(totalFat));
        totals.put("remainingKcal", round1(targetKcal - totalKcal));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", targetDate.toString());
        payload.put("basalKcal", round1(basalKcal));
        payload.put("targetKcal", round1(targetKcal));
        payload.put("totals", totals);
        payload.put("meals", meals);
        payload.put("favoriteFoods", foods.stream().filter(DietFood::isFavorite).map(this::toFoodPayload).toList());
        payload.put(
                "savedMeals",
            savedMealRepo.findByUserIdOrderByUpdatedAtDesc(userId)
                        .stream()
                        .map(this::toSavedMealPayload)
                        .toList()
        );
        return payload;
    }

    @GetMapping("/{userId}/saved-meals")
    public List<Map<String, Object>> getSavedMeals(@PathVariable Long userId) {
        ensureUserExists(userId);
        return savedMealRepo.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toSavedMealPayload)
                .toList();
    }

    @Transactional
    @PutMapping("/{userId}/saved-meals/{mealType}")
    public Map<String, Object> saveMealTemplate(
            @PathVariable Long userId,
            @PathVariable String mealType,
            @RequestBody SavedMealUpsertDto dto
    ) {
        ensureUserExists(userId);
        String normalizedMealType = normalizeMealType(mealType);

        if (dto == null || dto.items() == null || dto.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os itens da refeição para salvar");
        }

        DietSavedMeal savedMeal = savedMealRepo
                .findByUserIdAndMealTypeIgnoreCase(userId, normalizedMealType)
                .orElseGet(DietSavedMeal::new);

        String normalizedName = normalizeTemplateName(dto == null ? null : dto.name(), normalizedMealType);

        savedMeal.setUserId(userId);
        savedMeal.setMealType(normalizedMealType);
        savedMeal.setTemplateName(normalizedName);
        savedMeal.getItems().clear();

        int sortOrder = 0;
        for (SavedMealItemDto itemDto : dto.items()) {
            if (itemDto == null) continue;

            double quantity = normalizePositive(itemDto.quantityGrams(), "Quantidade do item");
            double calories = normalizeNonNegative(itemDto.calories(), "Calorias do item");
            double protein = normalizeNonNegative(itemDto.protein(), "Proteína do item");
            double carbs = normalizeNonNegative(itemDto.carbs(), "Carboidratos do item");
            double fat = normalizeNonNegative(itemDto.fat(), "Gordura do item");

            String foodName = itemDto.foodName() == null ? "" : itemDto.foodName().trim();
            if (foodName.isEmpty() && itemDto.foodId() != null) {
                foodName = foodRepo.findByIdAndUserId(itemDto.foodId(), userId)
                        .map(DietFood::getName)
                        .orElse("");
            }
            if (foodName.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do alimento do template é obrigatório");
            }

            DietSavedMealItem item = new DietSavedMealItem();
            item.setSavedMeal(savedMeal);
            item.setFoodId(itemDto.foodId());
            item.setFoodName(foodName);
            item.setQuantityGrams(quantity);
            item.setCalories(calories);
            item.setProtein(protein);
            item.setCarbs(carbs);
            item.setFat(fat);
            item.setSortOrder(sortOrder++);

            savedMeal.getItems().add(item);
        }

        if (savedMeal.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum item válido para salvar");
        }

        return toSavedMealPayload(savedMealRepo.save(savedMeal));
    }

    @Transactional
    @PostMapping("/{userId}/saved-meals/{savedMealId}/apply")
    public Map<String, Object> applySavedMeal(
            @PathVariable Long userId,
            @PathVariable Long savedMealId,
            @RequestBody(required = false) SavedMealApplyDto dto
    ) {
        ensureUserExists(userId);

        DietSavedMeal savedMeal = savedMealRepo
                .findByIdAndUserId(savedMealId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refeição salva não encontrada"));

        String targetMealType = normalizeMealType(dto == null ? null : dto.targetMealType());
        LocalDate entryDate = parseDate(dto == null ? null : dto.date());
        if (isNotToday(entryDate)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                "Somente o dia atual pode ser editado no diário"
            );
        }

        int appliedCount = 0;
        for (DietSavedMealItem templateItem : savedMeal.getItems()) {
            DietFood food = resolveFoodForSavedItem(userId, templateItem);
            if (food == null) continue;

            DietEntry entry = new DietEntry();
            entry.setUserId(userId);
            entry.setFoodId(food.getId());
            entry.setMealType(targetMealType);
            entry.setQuantityGrams(templateItem.getQuantityGrams());
            entry.setEntryDate(entryDate);
            entryRepo.save(entry);
            appliedCount++;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("savedMealId", savedMeal.getId());
        payload.put("templateName", savedMeal.getTemplateName());
        payload.put("mealType", targetMealType);
        payload.put("date", entryDate.toString());
        payload.put("appliedCount", appliedCount);
        return payload;
    }

    @Transactional
    @DeleteMapping("/{userId}/saved-meals/{savedMealId}")
    public void deleteSavedMeal(
            @PathVariable Long userId,
            @PathVariable Long savedMealId
    ) {
        ensureUserExists(userId);
        DietSavedMeal savedMeal = savedMealRepo.findByIdAndUserId(savedMealId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refeição salva não encontrada"));
        savedMealRepo.delete(savedMeal);
    }

    @PostMapping("/{userId}/entries")
    public Map<String, Object> addEntry(
            @PathVariable Long userId,
            @RequestBody EntryCreateDto dto
    ) {
        ensureUserExists(userId);

        if (dto == null || dto.foodId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "foodId é obrigatório");
        }

        DietFood food = foodRepo.findByIdAndUserId(dto.foodId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimento não encontrado"));

        String mealType = normalizeMealType(dto.mealType());
        double quantity = normalizePositive(dto.quantityGrams(), "Quantidade");
        LocalDate entryDate = parseDate(dto.date());
        if (isNotToday(entryDate)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Somente o dia atual pode ser editado no diário"
            );
        }

        DietEntry entry = new DietEntry();
        entry.setUserId(userId);
        entry.setFoodId(food.getId());
        entry.setMealType(mealType);
        entry.setQuantityGrams(quantity);
        entry.setEntryDate(entryDate);

        DietEntry saved = entryRepo.save(entry);
        double factor = quantity / 100.0;

        return toEntryPayload(
                saved,
                food,
                safe(food.getCaloriesPer100g()) * factor,
                safe(food.getProteinPer100g()) * factor,
                safe(food.getCarbsPer100g()) * factor,
                safe(food.getFatPer100g()) * factor
        );
    }

    @DeleteMapping("/{userId}/entries/{entryId}")
    public void deleteEntry(
            @PathVariable Long userId,
            @PathVariable Long entryId
    ) {
        ensureUserExists(userId);

        DietEntry entry = entryRepo.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));

        if (isNotToday(entry.getEntryDate())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                "Somente o dia atual pode ser editado no diário"
            );
        }

        entryRepo.delete(entry);
    }

    private boolean isNotToday(LocalDate date) {
        return date != null && !date.equals(LocalDate.now());
    }

    private void ensureUserExists(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId inválido");
        }
        if (userRepo.findById(userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
        }
    }

    private void validateFoodDto(FoodUpsertDto dto) {
        if (dto == null || dto.name() == null || dto.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do alimento é obrigatório");
        }
    }

    private String normalizeMealType(String rawMealType) {
        String value = rawMealType == null ? "" : rawMealType.trim();
        if (value.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refeição é obrigatória");
        }
        return value;
    }

    private String normalizeTemplateName(String rawTemplateName, String fallbackMealType) {
        String value = rawTemplateName == null ? "" : rawTemplateName.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return fallbackMealType + " - Favorito";
    }

    private LocalDate parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return LocalDate.now();
        }

        String value = rawDate.trim();
        if (value.length() >= 10) {
            value = value.substring(0, 10);
        }

        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data inválida. Use yyyy-MM-dd");
        }
    }

    private double normalizePositive(Double value, String field) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " deve ser maior que zero");
        }
        return value;
    }

    private double normalizeNonNegative(Double value, String field) {
        if (value == null || value < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " não pode ser negativo");
        }
        return value;
    }

    private Map<String, Object> toFoodPayload(DietFood food) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", food.getId());
        payload.put("userId", food.getUserId());
        payload.put("name", food.getName());
        payload.put("caloriesPer100g", round1(safe(food.getCaloriesPer100g())));
        payload.put("proteinPer100g", round1(safe(food.getProteinPer100g())));
        payload.put("carbsPer100g", round1(safe(food.getCarbsPer100g())));
        payload.put("fatPer100g", round1(safe(food.getFatPer100g())));
        payload.put("favorite", food.isFavorite());
        return payload;
    }

    private Map<String, Object> toEntryPayload(
            DietEntry entry,
            DietFood food,
            double calories,
            double protein,
            double carbs,
            double fat
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entry.getId());
        payload.put("userId", entry.getUserId());
        payload.put("foodId", food.getId());
        payload.put("foodName", food.getName());
        payload.put("mealType", entry.getMealType());
        payload.put("date", entry.getEntryDate().toString());
        payload.put("quantityGrams", round1(safe(entry.getQuantityGrams())));
        payload.put("calories", round1(calories));
        payload.put("protein", round1(protein));
        payload.put("carbs", round1(carbs));
        payload.put("fat", round1(fat));
        payload.put("favoriteFood", food.isFavorite());
        return payload;
    }

    private Map<String, Object> toSavedMealPayload(DietSavedMeal savedMeal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", savedMeal.getId());
        payload.put("userId", savedMeal.getUserId());
        payload.put("mealType", savedMeal.getMealType());
        payload.put("name", normalizeTemplateName(savedMeal.getTemplateName(), savedMeal.getMealType()));
        payload.put("createdAt", savedMeal.getCreatedAt() == null ? null : savedMeal.getCreatedAt().toString());
        payload.put("updatedAt", savedMeal.getUpdatedAt() == null ? null : savedMeal.getUpdatedAt().toString());
        payload.put(
                "items",
                savedMeal.getItems()
                        .stream()
                        .sorted(Comparator.comparing(DietSavedMealItem::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                        .map(item -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", item.getId());
                            row.put("foodId", item.getFoodId());
                            row.put("foodName", item.getFoodName());
                            row.put("quantityGrams", round1(safe(item.getQuantityGrams())));
                            row.put("calories", round1(safe(item.getCalories())));
                            row.put("protein", round1(safe(item.getProtein())));
                            row.put("carbs", round1(safe(item.getCarbs())));
                            row.put("fat", round1(safe(item.getFat())));
                            return row;
                        })
                        .toList()
        );
        return payload;
    }

    private DietFood resolveFoodForSavedItem(Long userId, DietSavedMealItem templateItem) {
        if (templateItem == null) return null;

        if (templateItem.getFoodId() != null) {
            Optional<DietFood> byId = foodRepo.findByIdAndUserId(templateItem.getFoodId(), userId);
            if (byId.isPresent()) {
                return byId.get();
            }
        }

        String name = templateItem.getFoodName() == null ? "" : templateItem.getFoodName().trim();
        if (!name.isEmpty()) {
            Optional<DietFood> byName = foodRepo.findByUserIdAndNameIgnoreCase(userId, name);
            if (byName.isPresent()) {
                return byName.get();
            }
        }

        if (name.isEmpty()) {
            return null;
        }

        double quantity = safe(templateItem.getQuantityGrams());
        if (quantity <= 0) quantity = 100.0;
        double factor = 100.0 / quantity;

        double caloriesPer100g = safe(templateItem.getCalories()) * factor;
        if (caloriesPer100g <= 0) {
            caloriesPer100g = 0.1;
        }

        DietFood created = new DietFood();
        created.setUserId(userId);
        created.setName(name);
        created.setCaloriesPer100g(caloriesPer100g);
        created.setProteinPer100g(Math.max(0.0, safe(templateItem.getProtein()) * factor));
        created.setCarbsPer100g(Math.max(0.0, safe(templateItem.getCarbs()) * factor));
        created.setFatPer100g(Math.max(0.0, safe(templateItem.getFat()) * factor));
        created.setFavorite(false);
        return foodRepo.save(created);
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private double safeNumber(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record FoodUpsertDto(
            String name,
            Double caloriesPer100g,
            Double proteinPer100g,
            Double carbsPer100g,
            Double fatPer100g,
            Boolean favorite
    ) {}

    public record FavoriteToggleDto(Boolean favorite) {}

    public record GoalUpsertDto(Double basalKcal, Double targetKcal) {}

    public record EntryCreateDto(Long foodId, String mealType, Double quantityGrams, String date) {}

    public record SavedMealItemDto(
            Long foodId,
            String foodName,
            Double quantityGrams,
            Double calories,
            Double protein,
            Double carbs,
            Double fat
    ) {}

    public record SavedMealUpsertDto(String name, List<SavedMealItemDto> items) {}

    public record SavedMealApplyDto(String date, String targetMealType) {}
}
