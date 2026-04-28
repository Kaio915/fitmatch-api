package fitmatch_api.security;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class JwtRoles {
    private JwtRoles() {}

    static Set<String> extractRoles(Object rolesObj) {
        if (rolesObj == null) {
            return Set.of();
        }
        if (rolesObj instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(v -> v != null)
                    .map(v -> v.toString().trim())
                    .filter(v -> !v.isBlank())
                    .map(v -> v.toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        }
        // fallback: "ROLE1,ROLE2"
        String raw = rolesObj.toString();
        if (raw == null || raw.isBlank()) return Set.of();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .map(v -> v.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
