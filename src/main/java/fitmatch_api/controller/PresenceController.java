package fitmatch_api.controller;

import fitmatch_api.repository.UserRepository;
import fitmatch_api.security.AuthContext;
import fitmatch_api.security.JwtPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/presence")
public class PresenceController {

    private final UserRepository userRepo;

    public PresenceController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // Atualiza o timestamp de presença do usuário autenticado
    @PutMapping("/heartbeat")
    public void heartbeat() {
        JwtPrincipal principal = AuthContext.requirePrincipal();
        userRepo.findById(principal.userId()).ifPresent(user -> {
            user.setLastOnlineAt(LocalDateTime.now());
            userRepo.save(user);
        });
    }

    // Retorna se um usuário está online (ativo nos últimos 2 minutos)
    @GetMapping("/{userId}")
    public Map<String, Object> getPresence(@PathVariable Long userId) {
        AuthContext.requirePrincipal();
        return userRepo.findById(userId).map(user -> {
            boolean online = user.getLastOnlineAt() != null
                    && user.getLastOnlineAt().isAfter(LocalDateTime.now().minusMinutes(2));
            return Map.<String, Object>of("online", online);
        }).orElse(Map.of("online", false));
    }
}
