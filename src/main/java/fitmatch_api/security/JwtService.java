package fitmatch_api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class JwtService {
    private static final Base64.Encoder B64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds:86400}") long expirationSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;

        if (this.secret.length < 32) {
            // 32 bytes ~ 256 bits. Menor que isso é fácil de brute-force.
            throw new IllegalStateException("app.jwt.secret deve ter pelo menos 32 caracteres");
        }
    }

    public String issueToken(Long userId, Set<String> roles) {
        if (userId == null) {
            throw new IllegalArgumentException("userId é obrigatório");
        }
        Instant now = Instant.now();
        long iat = now.getEpochSecond();
        long exp = now.plusSeconds(expirationSeconds).getEpochSecond();

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", String.valueOf(userId));
        payload.put("iat", iat);
        payload.put("exp", exp);
        payload.put("roles", roles == null ? Set.of() : roles);

        String encodedHeader = base64UrlJson(header);
        String encodedPayload = base64UrlJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = hmacSha256Base64Url(signingInput);
        return signingInput + "." + signature;
    }

    public JwtPrincipal verifyAndParse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = hmacSha256Base64Url(signingInput);
        if (!constantTimeEquals(expectedSig, parts[2])) {
            return null;
        }

        Map<String, Object> payload;
        try {
            payload = parseJsonMap(parts[1]);
        } catch (Exception e) {
            return null;
        }

        Object expObj = payload.get("exp");
        long exp = asLong(expObj, -1);
        if (exp <= 0 || Instant.now().getEpochSecond() >= exp) {
            return null;
        }

        String sub = payload.get("sub") == null ? null : payload.get("sub").toString();
        Long userId = null;
        try {
            if (sub != null && !sub.isBlank()) userId = Long.parseLong(sub);
        } catch (NumberFormatException ignored) {}
        if (userId == null) {
            return null;
        }

        Set<String> roles = JwtRoles.extractRoles(payload.get("roles"));
        return new JwtPrincipal(userId, roles);
    }

    private String base64UrlJson(Map<String, Object> map) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(map);
            return B64_URL_ENCODER.encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String b64UrlJson) throws Exception {
        byte[] json = B64_URL_DECODER.decode(b64UrlJson);
        return objectMapper.readValue(json, Map.class);
    }

    private String hmacSha256Base64Url(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] sig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return B64_URL_ENCODER.encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar token", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private long asLong(Object value, long fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }
}
