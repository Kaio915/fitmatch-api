package fitmatch_api.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EdamamFoodService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${diet.edamam.enabled:true}")
    private boolean enabled;

    @Value("${diet.edamam.app-id:}")
    private String appId;

    @Value("${diet.edamam.app-key:}")
    private String appKey;

    @Value("${diet.edamam.base-url:https://api.edamam.com}")
    private String baseUrl;

    @Value("${diet.edamam.account-user:}")
    private String accountUser;

    public EdamamFoodService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public List<Map<String, Object>> searchFoods(Long userId, String query, Integer rawLimit) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integração com Edamam está desabilitada");
        }

        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Edamam não configurado. Defina diet.edamam.app-id e diet.edamam.app-key"
            );
        }

        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um termo para busca");
        }

        int limit = rawLimit == null ? 12 : Math.max(1, Math.min(rawLimit, 20));

        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String normalizedBaseUrl = baseUrl == null ? "https://api.edamam.com" : baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }

        String url = normalizedBaseUrl
                + "/api/food-database/v2/parser"
                + "?app_id=" + URLEncoder.encode(appId.trim(), StandardCharsets.UTF_8)
                + "&app_key=" + URLEncoder.encode(appKey.trim(), StandardCharsets.UTF_8)
                + "&ingr=" + encoded
                + "&nutrition-type=cooking";

        String edamamAccountUser = resolveAccountUser(userId);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Edamam-Account-User", edamamAccountUser)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String providerMessage = extractProviderMessage(response.body());
                String message;

                if (response.statusCode() == 401) {
                    message = "Falha de autenticação no Edamam (401). Verifique APP_ID/APP_KEY da Food Database API e Edamam-Account-User.";
                    if (!providerMessage.isBlank()) {
                        message += " Detalhe do provedor: " + providerMessage;
                    }
                } else {
                    message = "Falha ao consultar Edamam (status " + response.statusCode() + ")";
                    if (!providerMessage.isBlank()) {
                        message += ": " + providerMessage;
                    }
                }

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        message
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            List<Map<String, Object>> results = new ArrayList<>();
            Set<String> dedupe = new LinkedHashSet<>();

            for (JsonNode item : root.path("parsed")) {
                pushCandidate(item.path("food"), results, dedupe, limit);
                if (results.size() >= limit) {
                    return results;
                }
            }

            for (JsonNode item : root.path("hints")) {
                pushCandidate(item.path("food"), results, dedupe, limit);
                if (results.size() >= limit) {
                    return results;
                }
            }

            return results;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erro ao consultar Edamam");
        }
    }

    private void pushCandidate(
            JsonNode foodNode,
            List<Map<String, Object>> results,
            Set<String> dedupe,
            int limit
    ) {
        if (foodNode == null || foodNode.isMissingNode() || results.size() >= limit) {
            return;
        }

        String label = foodNode.path("label").asText("").trim();
        if (label.isEmpty()) {
            return;
        }

        String key = label.toLowerCase();
        if (dedupe.contains(key)) {
            return;
        }

        JsonNode nutrients = foodNode.path("nutrients");
        double kcal = nutrients.path("ENERC_KCAL").asDouble(0);
        double protein = nutrients.path("PROCNT").asDouble(0);
        double carbs = nutrients.path("CHOCDF").asDouble(0);
        double fat = nutrients.path("FAT").asDouble(0);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", label);
        row.put("caloriesPer100g", round1(kcal));
        row.put("proteinPer100g", round1(protein));
        row.put("carbsPer100g", round1(carbs));
        row.put("fatPer100g", round1(fat));
        row.put("brand", foodNode.path("brand").asText(""));
        row.put("category", foodNode.path("category").asText(""));
        row.put("source", "edamam");

        dedupe.add(key);
        results.add(row);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String resolveAccountUser(Long userId) {
        String raw = accountUser == null ? "" : accountUser.trim();
        if (raw.isEmpty()) {
            raw = "fitmatch-" + (userId == null ? "local" : userId);
        }

        String sanitized = raw.replaceAll("[^A-Za-z0-9_-]", "_");
        if (sanitized.length() > 30) {
            sanitized = sanitized.substring(0, 30);
        }
        if (sanitized.isBlank()) {
            return "fitmatch-local";
        }
        return sanitized;
    }

    private String extractProviderMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String fromMessage = root.path("message").asText("").trim();
            if (!fromMessage.isEmpty()) {
                return fromMessage;
            }
            String fromError = root.path("error").asText("").trim();
            if (!fromError.isEmpty()) {
                return fromError;
            }
        } catch (Exception ignored) {
            // If provider body is not JSON, return empty to avoid leaking noisy HTML.
        }

        return "";
    }
}