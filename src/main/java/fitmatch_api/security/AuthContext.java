package fitmatch_api.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

public final class AuthContext {
    private AuthContext() {}

    public static JwtPrincipal principalOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal;
        }
        return null;
    }

    public static JwtPrincipal requirePrincipal() {
        JwtPrincipal principal = principalOrNull();
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado");
        }
        return principal;
    }

    public static boolean isAdmin(JwtPrincipal principal) {
        return principal != null && principal.hasRole("ADMIN");
    }

    public static void requireSelfOrAdmin(Long targetUserId) {
        JwtPrincipal principal = requirePrincipal();
        if (targetUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId é obrigatório");
        }
        if (isAdmin(principal)) {
            return;
        }
        if (principal.userId() == null || !principal.userId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }

    public static void requireRole(String role) {
        JwtPrincipal principal = requirePrincipal();
        if (!principal.hasRole(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }

    public static void requireSelfOrAdminFromAny(Long... ids) {
        JwtPrincipal principal = requirePrincipal();
        if (isAdmin(principal)) return;
        if (principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
        if (ids == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
        for (Long id : ids) {
            if (id != null && principal.userId().equals(id)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
    }
}
