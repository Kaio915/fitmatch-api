package fitmatch_api.service;

import fitmatch_api.dto.AlimentoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FatSecretService {

    private static final Logger log = LoggerFactory.getLogger(FatSecretService.class);

    private static final Map<String, String> EN_TO_PT = Map.ofEntries(
            Map.entry("brazilian", "Brasileiro"),
            Map.entry("rice", "Arroz"),
            Map.entry("corn", "Milho"),
            Map.entry("maize", "Milho"),
            Map.entry("beef", "Carne Bovina"),
            Map.entry("meat", "Carne"),
            Map.entry("pork", "Carne Suína"),
            Map.entry("chicken", "Frango"),
            Map.entry("turkey", "Peru"),
            Map.entry("lamb", "Cordeiro"),
            Map.entry("tuna", "Atum"),
            Map.entry("salmon", "Salmão"),
            Map.entry("style", "Estilo"),
            Map.entry("steak", "Bife"),
            Map.entry("bread", "Pão"),
            Map.entry("oat", "Aveia"),
            Map.entry("oats", "Aveia"),
            Map.entry("wheat", "Trigo"),
            Map.entry("quinoa", "Quinoa"),
            Map.entry("cheese", "Queijo"),
            Map.entry("milk", "Leite"),
            Map.entry("yogurt", "Iogurte"),
            Map.entry("yoghurt", "Iogurte"),
            Map.entry("egg", "Ovo"),
            Map.entry("eggs", "Ovos"),
            Map.entry("fish", "Peixe"),
            Map.entry("shrimp", "Camarão"),
            Map.entry("bean", "Feijão"),
            Map.entry("beans", "Feijões"),
            Map.entry("lentil", "Lentilha"),
            Map.entry("lentils", "Lentilhas"),
            Map.entry("chickpea", "Grão-de-bico"),
            Map.entry("chickpeas", "Grão-de-bico"),
            Map.entry("pea", "Ervilha"),
            Map.entry("peas", "Ervilhas"),
            Map.entry("sugar", "Açúcar"),
            Map.entry("salt", "Sal"),
            Map.entry("oil", "Óleo"),
            Map.entry("butter", "Manteiga"),
            Map.entry("peanut", "Amendoim"),
            Map.entry("peanuts", "Amendoim"),
            Map.entry("almond", "Amêndoa"),
            Map.entry("almonds", "Amêndoas"),
            Map.entry("walnut", "Noz"),
            Map.entry("walnuts", "Nozes"),
            Map.entry("flour", "Farinha"),
            Map.entry("sauce", "Molho"),
            Map.entry("fried", "Frito"),
            Map.entry("grilled", "Grelhado"),
            Map.entry("roasted", "Assado"),
            Map.entry("boiled", "Cozido"),
            Map.entry("cooked", "Cozido"),
            Map.entry("raw", "Cru"),
            Map.entry("sweet", "Doce"),
            Map.entry("apple", "Maçã"),
            Map.entry("banana", "Banana"),
            Map.entry("orange", "Laranja"),
            Map.entry("strawberry", "Morango"),
            Map.entry("grape", "Uva"),
            Map.entry("pineapple", "Abacaxi"),
            Map.entry("watermelon", "Melancia"),
            Map.entry("avocado", "Abacate"),
            Map.entry("tomato", "Tomate"),
            Map.entry("potato", "Batata"),
            Map.entry("onion", "Cebola"),
            Map.entry("garlic", "Alho"),
            Map.entry("water", "Água"),
            Map.entry("juice", "Suco"),
            Map.entry("coffee", "Café"),
            Map.entry("tea", "Chá"),
            Map.entry("soda", "Refrigerante"),
            Map.entry("cake", "Bolo"),
            Map.entry("cookie", "Biscoito"),
            Map.entry("cookies", "Biscoitos"),
            Map.entry("pasta", "Massa"),
            Map.entry("noodles", "Macarrão")
    );

            private static final Map<String, String> PT_TO_EN_PRIORITY = Map.ofEntries(
                Map.entry("milho", "corn"),
                Map.entry("carne", "meat"),
                Map.entry("carne bovina", "beef"),
                Map.entry("carne suína", "pork"),
                Map.entry("frango", "chicken")
            );

    private static final Pattern ENGLISH_TERM_PATTERN = Pattern.compile(
            "\\b(" + String.join("|", EN_TO_PT.keySet()) + ")\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

        private static final Pattern EXCLUDED_FOOD_NAME_PATTERN = Pattern.compile(
            "\\b(?:con\\s+pollo|con\\s+leche|con\\s+vegetables|puerto\\s+rican|mexican|style|stuffed)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );

    private final ObjectMapper objectMapper;
        private final RestTemplate restTemplate;

    @Value("${fatsecret.client.id}")
    private String clientId;

    @Value("${fatsecret.client.secret}")
    private String clientSecret;

    @Value("${fatsecret.oauth.url}")
    private String oauthUrl;

    @Value("${fatsecret.api.url}")
    private String apiUrl;

    private volatile String accessToken;
    private volatile long tokenExpiresAtMillis;

    public FatSecretService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public List<AlimentoDTO> searchFoods(String termo) {
        if (termo == null || termo.trim().length() < 2) {
            return List.of();
        }

        String token;
        try {
            token = getAccessToken();
        } catch (ResourceAccessException ex) {
            log.warn("Timeout ao obter token do FatSecret. Retornando lista vazia.", ex);
            return List.of();
        } catch (Exception ex) {
            log.error("Falha ao obter token do FatSecret: {}", ex.getMessage(), ex);
            return List.of();
        }
        if (token == null || token.isBlank()) {
            return List.of();
        }

        try {
            String termoNormalizado = termo.trim();
            String fallbackTermo = findEnglishFallback(termoNormalizado);
            List<JsonNode> items;
            if (fallbackTermo != null && !fallbackTermo.equalsIgnoreCase(termoNormalizado)) {
                log.info("Buscando '{}' pelo termo equivalente em inglês: '{}'.", termoNormalizado, fallbackTermo);
                items = searchFoodItems(token, fallbackTermo);
                if (items.isEmpty()) {
                    items = searchFoodItems(token, termoNormalizado);
                }
            } else {
                items = searchFoodItems(token, termoNormalizado);
            }

            int filtered = (int) items.stream()
                    .filter(item -> isExcludedFoodName(textOf(item.path("food_name"))))
                    .count();
            items.removeIf(item -> isExcludedFoodName(textOf(item.path("food_name"))));

            // Alimentos simples e curtos aparecem antes de pratos compostos.
            items.sort(Comparator
                    .comparingInt((JsonNode item) -> textOf(item.path("food_name")).length())
                    .thenComparingInt(item -> countWords(textOf(item.path("food_name"))))
                    .thenComparing((a, b) -> Integer.compare(
                        foodNameRanking(textOf(b.path("food_name"))),
                        foodNameRanking(textOf(a.path("food_name")))
                    )));

            List<AlimentoDTO> results = new ArrayList<>();
            int parsed = 0;
            int failed = 0;
            int total = items.size();

            for (JsonNode item : items) {
                AlimentoDTO dto = toAlimento(item);
                if (dto != null) {
                    results.add(dto);
                    parsed++;
                } else {
                    failed++;
                }
            }

            log.info("FatSecret: {} alimento(s) encontrado(s), {} filtrado(s), {} mapeado(s), {} falha(s) de parse para o termo '{}'.", total, filtered, parsed, failed, termo);
            return results;
        } catch (ResourceAccessException ex) {
            log.warn("Timeout ao consultar alimentos no FatSecret para o termo '{}'. Retornando lista vazia.", termo, ex);
            return List.of();
        } catch (Exception ex) {
            log.error("Erro ao consultar FatSecret para o termo '{}': {}", termo, ex.getMessage(), ex);
            return List.of();
        }
    }

    private List<JsonNode> searchFoodItems(String token, String searchTerm) throws Exception {
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String url = apiUrl
                + "?method=foods.search"
                + "&search_expression=" + encodedTerm
                + "&format=json"
                + "&page_number=0"
                + "&max_results=20";

        log.info("Consultando FatSecret: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(url),
                HttpMethod.GET,
                request,
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("FatSecret retornou HTTP {}. Resposta: {}", response.getStatusCode(), response.getBody());
            return List.of();
        }
        log.info("Resposta FatSecret (HTTP {}): {}", response.getStatusCode(), response.getBody());

        JsonNode foodNode = objectMapper.readTree(response.getBody()).path("foods").path("food");
        List<JsonNode> items = new ArrayList<>();
        if (foodNode.isArray()) {
            for (JsonNode item : foodNode) {
                items.add(item);
            }
        } else if (foodNode.isObject()) {
            items.add(foodNode);
        }
        return items;
    }

    private String findEnglishFallback(String term) {
        String normalizedTerm = term.toLowerCase(Locale.ROOT);
        String prioritizedTerm = PT_TO_EN_PRIORITY.get(normalizedTerm);
        if (prioritizedTerm != null) {
            return prioritizedTerm;
        }
        return EN_TO_PT.entrySet().stream()
                .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).equals(normalizedTerm))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private synchronized String getAccessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAtMillis - 60_000L) {
            log.info("FatSecret: reutilizando token OAuth em cache.");
            return accessToken;
        }

        log.info("FatSecret: solicitando token OAuth (client_credentials).");

        String credentials = clientId + ":" + clientSecret;
        String basic = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, clientSecret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<String> request = new HttpEntity<>(
            "grant_type=client_credentials&scope=basic",
            headers
        );

        ResponseEntity<String> response = restTemplate.exchange(
            URI.create(oauthUrl),
            HttpMethod.POST,
            request,
            String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("FatSecret OAuth retornou HTTP {}. Resposta: {}", response.getStatusCode(), response.getBody());
            throw new IllegalStateException("Falha na autenticação FatSecret (HTTP " + response.getStatusCode() + ")");
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String token = textOf(root.path("access_token"));
        long expiresIn = (long) root.path("expires_in").asDouble(0.0);

        if (token.isBlank()) {
            throw new IllegalStateException("Token de acesso ausente na resposta do FatSecret OAuth");
        }

        this.accessToken = token;
        this.tokenExpiresAtMillis = System.currentTimeMillis()
                + (expiresIn > 0 ? expiresIn * 1000L : 3600_000L);

        log.info("FatSecret: token OAuth obtido com sucesso (expira em {}s).", expiresIn);
        return token;
    }

    private AlimentoDTO toAlimento(JsonNode food) {
        if (food == null || food.isMissingNode()) {
            return null;
        }

        String name = translateName(textOf(food.path("food_name")));
        if (name.isBlank()) {
            log.warn("FatSecret: alimento ignorado por nome ausente.");
            return null;
        }

        String description = textOf(food.path("food_description"));
        double kcal = parseNutrient(description,
                "Calorias\\s*:\\s*([\\d.,]+)",
                "Calories\\s*:\\s*([\\d.,]+)",
                "([\\d.,]+)\\s*kcal");
        double protein = parseNutrient(description,
                "Prote[íi]na[s]?\\s*:\\s*([\\d.,]+)",
                "Protein\\s*:\\s*([\\d.,]+)");
        double carbs = parseNutrient(description,
                "Carb[s]?\\s*:\\s*([\\d.,]+)",
                "Carboidrato[s]?\\s*:\\s*([\\d.,]+)",
                "Carbohydrates\\s*:\\s*([\\d.,]+)");
        double fat = parseNutrient(description,
                "Gordura[s]?\\s*:\\s*([\\d.,]+)",
                "Fat\\s*:\\s*([\\d.,]+)");

        // Normaliza os macronutrientes para a base de 100 g. A FatSecret descreve
        // a porção no próprio "food_description" (ex.: "Per 1 ovo (50 g) - ...").
        double servingGrams = parseServingGrams(description);
        if (servingGrams > 0 && Math.abs(servingGrams - 100.0) > 0.0001) {
            double scale = 100.0 / servingGrams;
            kcal *= scale;
            protein *= scale;
            carbs *= scale;
            fat *= scale;
        }

        String servingDescription = parseServingDescription(description);
        String servingUnit = parseServingUnit(description);

        return new AlimentoDTO(
                name,
                round1(kcal),
                round1(protein),
                round1(carbs),
                round1(fat),
                textOf(food.path("brand_name")),
                textOf(food.path("food_type")),
                "FatSecret",
                servingDescription,
                servingGrams > 0 ? round1(servingGrams) : null,
                servingUnit
        );
    }

    private String parseServingDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String value = description.trim();
        if (!value.toLowerCase(Locale.ROOT).startsWith("per ")) {
            return "";
        }
        int dashIndex = value.indexOf(" - ");
        if (dashIndex > 4) {
            return value.substring(4, dashIndex).trim();
        }
        return value.substring(4).trim();
    }

    private double parseServingGrams(String description) {
        if (description == null || description.isBlank()) {
            return 0.0;
        }
        // Ex.: "Per 1 ovo (50 g) - ..." ou "Per 100 g - ..."
        Matcher parenthesized = Pattern.compile(
                "\\(\\s*([\\d.,]+)\\s*(?:g|gramas?|ml)\\s*\\)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(description);
        if (parenthesized.find()) {
            return parseDecimal(parenthesized.group(1));
        }
        Matcher perAmount = Pattern.compile(
                "Per\\s+([\\d.,]+)\\s*(?:g|gramas?|ml)\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(description);
        if (perAmount.find()) {
            return parseDecimal(perAmount.group(1));
        }
        return 0.0;
    }

    private String parseServingUnit(String description) {
        if (description == null || description.isBlank()) {
            return "g";
        }
        Matcher parenthesized = Pattern.compile(
                "\\(\\s*[\\d.,]+\\s*(g|gramas?|ml)\\s*\\)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ).matcher(description);
        if (parenthesized.find()) {
            String unit = parenthesized.group(1);
            return unit.toLowerCase(Locale.ROOT).startsWith("ml") ? "ml" : "g";
        }
        return "g";
    }

    private double parseDecimal(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private double parseNutrient(String description, String... patterns) {
        if (description == null || description.isBlank()) {
            return 0.0;
        }
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(description);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1).replace(',', '.'));
                } catch (NumberFormatException ignored) {
                    // tenta o próximo padrão
                }
            }
        }
        return 0.0;
    }

    private String translateName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String result = name;
        for (Map.Entry<String, String> entry : EN_TO_PT.entrySet()) {
            result = result.replaceAll(
                    "(?i)\\b" + Pattern.quote(entry.getKey()) + "\\b",
                    Matcher.quoteReplacement(entry.getValue())
            );
        }
        return result;
    }

    private boolean isEnglishFoodName(String name) {
        return name != null && !name.isBlank() && ENGLISH_TERM_PATTERN.matcher(name).find();
    }

    private boolean isExcludedFoodName(String name) {
        return name != null && EXCLUDED_FOOD_NAME_PATTERN.matcher(name).find();
    }

    private int countWords(String name) {
        return name == null || name.isBlank() ? 0 : name.trim().split("\\s+").length;
    }

    private int foodNameRanking(String name) {
        if (name == null || name.isBlank()) {
            return 0;
        }
        if (isEnglishFoodName(name)) {
            return 0;
        }
        return name.matches(".*[áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ].*") ? 2 : 1;
    }

    private String textOf(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isArray()) {
            return node.isEmpty() ? "" : textOf(node.get(0));
        }
        if (node.isValueNode()) {
            return node.asString().trim();
        }
        return node.toString();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
