package fitmatch_api.service;

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
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CrefValidationService {

    private static final Pattern CREF_PATTERN = Pattern.compile("^(\\d{4,10})(?:-?([A-Z]))?$");
    private static final Set<String> VALID_UFS = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
            "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC",
            "SP", "SE", "TO"
    );

    private static final Set<String> ACTIVE_KEYWORDS = Set.of(
            "ativo", "regular", "regularizado", "adimplente", "apto"
    );
    private static final Set<String> INACTIVE_KEYWORDS = Set.of(
            "inativo", "cancelado", "suspenso", "irregular", "baixado", "cassado"
    );
    private static final Set<String> NOT_FOUND_KEYWORDS = Set.of(
            "nao encontrado", "nao localizada", "nao localizado", "inexistente"
    );

    private final HttpClient httpClient;

    @Value("${cref.validation.auto-lookup-enabled:false}")
    private boolean autoLookupEnabled;

    @Value("${cref.validation.auto-lookup-url-template:}")
    private String autoLookupUrlTemplate;

    @Value("${cref.validation.manual-url-template:https://www.confef.org.br/confef/registrados/}")
    private String manualUrlTemplate;

    @Value("${cref.validation.auto-timeout-seconds:8}")
    private int autoTimeoutSeconds;

    public CrefValidationService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public CrefValidationResult validate(String rawCref, String rawUf) {
        String input = rawCref == null ? "" : rawCref.trim();
        if (input.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o CREF");
        }

        List<ValidationStep> steps = new ArrayList<>();
        NormalizedCref normalized = normalize(rawCref, rawUf);

        if (!normalized.valid()) {
            steps.add(new ValidationStep("format", "invalid", normalized.message()));
            return new CrefValidationResult(
                    input,
                    normalized.normalizedCref(),
                    normalized.uf(),
                    false,
                    normalized.message(),
                    "FORMAT_INVALID",
                    "LOCAL_REGEX",
                    "fitmatch-api",
                    null,
                    buildManualUrl(normalized.uf(), normalized.normalizedCref()),
                    "Formato invalido. Corrija antes de consultar status oficial.",
                    steps
            );
        }

        steps.add(new ValidationStep("format", "ok", "Formato compativel com CREF + UF."));

        String manualUrl = buildManualUrl(normalized.uf(), normalized.normalizedCref());

        if (!autoLookupEnabled || isBlank(autoLookupUrlTemplate)) {
            steps.add(new ValidationStep("auto_lookup", "skipped", "Consulta automatica desabilitada por configuracao."));
            steps.add(new ValidationStep("manual_fallback", "required", "Use o link oficial/manual para confirmar status real."));
            return new CrefValidationResult(
                    input,
                    normalized.normalizedCref(),
                    normalized.uf(),
                    true,
                    normalized.message(),
                    "MANUAL_CHECK_REQUIRED",
                    "MANUAL_PORTAL",
                    "Portal CREF/CONFEF",
                    null,
                    manualUrl,
                    "Formato valido. Falta confirmar status oficial no portal.",
                    steps
            );
        }

        String lookupUrl = buildLookupUrl(normalized.uf(), normalized.normalizedCref());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(lookupUrl))
                    .timeout(Duration.ofSeconds(Math.max(2, autoTimeoutSeconds)))
                    .header("Accept", "application/json, text/plain, text/html")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                steps.add(new ValidationStep(
                        "auto_lookup",
                        "failed",
                        "Provedor retornou status " + response.statusCode() + "."
                ));
                steps.add(new ValidationStep("manual_fallback", "required", "Falha na consulta automatica."));
                return new CrefValidationResult(
                        input,
                        normalized.normalizedCref(),
                        normalized.uf(),
                        true,
                        normalized.message(),
                        "UNAVAILABLE",
                        "AUTO_HTTP",
                        "Configured CREF Provider",
                        lookupUrl,
                        manualUrl,
                        "Nao foi possivel confirmar status automaticamente. Consulte manualmente.",
                        steps
                );
            }

            String interpretedStatus = interpretStatus(response.body());
            String message = switch (interpretedStatus) {
                case "ACTIVE" -> "Registro aparenta estar ativo (confirmar no portal oficial).";
                case "INACTIVE" -> "Registro aparenta estar inativo/irregular.";
                case "NOT_FOUND" -> "Registro nao encontrado na consulta automatica.";
                default -> "Resposta recebida, mas sem status conclusivo. Use a consulta manual.";
            };

            steps.add(new ValidationStep("auto_lookup", "ok", "Consulta automatica executada."));
            if ("UNAVAILABLE".equals(interpretedStatus)) {
                steps.add(new ValidationStep("manual_fallback", "required", "Status inconclusivo na resposta automatica."));
            }

            return new CrefValidationResult(
                    input,
                    normalized.normalizedCref(),
                    normalized.uf(),
                    true,
                    normalized.message(),
                    interpretedStatus,
                    "AUTO_HTTP",
                    "Configured CREF Provider",
                    lookupUrl,
                    manualUrl,
                    message,
                    steps
            );
        } catch (Exception ex) {
            steps.add(new ValidationStep("auto_lookup", "failed", "Erro tecnico na consulta automatica."));
            steps.add(new ValidationStep("manual_fallback", "required", "Use o portal oficial para concluir validacao."));
            return new CrefValidationResult(
                    input,
                    normalized.normalizedCref(),
                    normalized.uf(),
                    true,
                    normalized.message(),
                    "UNAVAILABLE",
                    "AUTO_HTTP",
                    "Configured CREF Provider",
                    lookupUrl,
                    manualUrl,
                    "Falha na consulta automatica. Valide manualmente no portal.",
                    steps
            );
        }
    }

    private NormalizedCref normalize(String rawCref, String rawUf) {
        String cref = safe(rawCref).toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("CREF", "");

        String ufFromInput = null;
        Matcher slashUf = Pattern.compile("/([A-Z]{2})$").matcher(cref);
        if (slashUf.find()) {
            ufFromInput = slashUf.group(1);
            cref = cref.substring(0, cref.length() - 3);
        }

        String uf = safe(rawUf).toUpperCase(Locale.ROOT);
        if (uf.isEmpty()) {
            uf = ufFromInput == null ? "" : ufFromInput;
        }

        if (uf.isEmpty()) {
            Matcher trailingUf = Pattern.compile("([A-Z]{2})$").matcher(cref);
            if (trailingUf.find()) {
                String maybeUf = trailingUf.group(1);
                if (VALID_UFS.contains(maybeUf)) {
                    uf = maybeUf;
                    cref = cref.substring(0, cref.length() - 2);
                }
            }
        }

        if (!VALID_UFS.contains(uf)) {
            return new NormalizedCref(false, null, uf.isEmpty() ? null : uf, "UF do CREF invalida ou ausente.");
        }

        String core = cref.replaceAll("[^A-Z0-9-]", "");
        Matcher coreMatcher = CREF_PATTERN.matcher(core);
        if (!coreMatcher.matches()) {
            return new NormalizedCref(false, null, uf, "Formato invalido. Ex.: 123456-G/SP");
        }

        String number = coreMatcher.group(1);
        String category = coreMatcher.group(2);
        String normalized = category == null || category.isBlank()
                ? number + "/" + uf
                : number + "-" + category + "/" + uf;

        String message = category == null || category.isBlank()
                ? "CREF normalizado sem categoria (letra)."
                : "CREF normalizado com categoria e UF.";

        return new NormalizedCref(true, normalized, uf, message);
    }

    private String interpretStatus(String body) {
        if (isBlank(body)) {
            return "UNAVAILABLE";
        }

        String normalized = normalizeForSearch(body);

        if (containsAny(normalized, NOT_FOUND_KEYWORDS)) {
            return "NOT_FOUND";
        }
        if (containsAny(normalized, INACTIVE_KEYWORDS)) {
            return "INACTIVE";
        }
        if (containsAny(normalized, ACTIVE_KEYWORDS)) {
            return "ACTIVE";
        }
        return "UNAVAILABLE";
    }

    private boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(normalizeForSearch(keyword))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForSearch(String text) {
        String lower = safe(text).toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private String buildLookupUrl(String uf, String cref) {
        String encodedUf = URLEncoder.encode(safe(uf), StandardCharsets.UTF_8);
        String encodedCref = URLEncoder.encode(safe(cref), StandardCharsets.UTF_8);
        return autoLookupUrlTemplate
                .replace("{uf}", encodedUf)
                .replace("{cref}", encodedCref);
    }

    private String buildManualUrl(String uf, String cref) {
        String template = isBlank(manualUrlTemplate)
                ? "https://www.confef.org.br/confef/registrados/"
                : manualUrlTemplate;

        return template
                .replace("{uf}", URLEncoder.encode(safe(uf), StandardCharsets.UTF_8))
                .replace("{cref}", URLEncoder.encode(safe(cref), StandardCharsets.UTF_8));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record NormalizedCref(boolean valid, String normalizedCref, String uf, String message) {
    }

    public record ValidationStep(String step, String status, String detail) {
    }

    public record CrefValidationResult(
            String input,
            String normalizedCref,
            String uf,
            boolean formatValid,
            String formatMessage,
            String officialStatus,
            String sourceType,
            String sourceName,
            String sourceUrl,
            String manualCheckUrl,
            String message,
            List<ValidationStep> steps
    ) {
    }
}
