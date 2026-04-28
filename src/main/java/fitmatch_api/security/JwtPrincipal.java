package fitmatch_api.security;

import java.util.Set;

public record JwtPrincipal(Long userId, Set<String> roles) {
    public boolean hasRole(String role) {
        if (role == null) return false;
        return roles != null && roles.contains(role.toUpperCase());
    }
}
