package fitmatch_api.controller;

import fitmatch_api.repository.UserRepository;
import fitmatch_api.security.AuthContext;
import fitmatch_api.security.JwtPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/presence")
public class PresenceController {

    private final UserRepository userRepo;

    // Armazena o estado de "digitando" em memória: userId → {receiverId, typingAt}
    private static final ConcurrentHashMap<Long, TypingEntry> typingMap = new ConcurrentHashMap<>();

    private record TypingEntry(Long receiverId, LocalDateTime typingAt) {}

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

    // Marca o usuário autenticado como offline imediatamente
    @PutMapping("/offline")
    public void offline() {
        JwtPrincipal principal = AuthContext.requirePrincipal();
        userRepo.findById(principal.userId()).ifPresent(user -> {
            user.setLastOnlineAt(LocalDateTime.now().minusYears(1));
            userRepo.save(user);
        });
        typingMap.remove(principal.userId());
    }

    // Registra que o usuário autenticado está digitando para um destinatário.
    // Se receiverId for null ou ausente, remove o indicador de digitação.
    @PutMapping("/typing")
    public void typing(@RequestBody Map<String, Long> body) {
        JwtPrincipal principal = AuthContext.requirePrincipal();
        Long receiverId = body.get("receiverId");
        if (receiverId != null) {
            typingMap.put(principal.userId(), new TypingEntry(receiverId, LocalDateTime.now()));
        } else {
            typingMap.remove(principal.userId());
        }
    }

    // Retorna se um usuário está digitando (observado por outro usuário)
    @GetMapping("/{userId}/typing")
    public Map<String, Object> getTyping(@PathVariable Long userId,
                                         @RequestParam Long observerId) {
        AuthContext.requirePrincipal();
        TypingEntry entry = typingMap.get(userId);
        boolean typing = entry != null
                && entry.receiverId().equals(observerId)
                && entry.typingAt().isAfter(LocalDateTime.now().minusSeconds(5));
        return Map.of("typing", typing);
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
