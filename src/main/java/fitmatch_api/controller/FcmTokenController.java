package fitmatch_api.controller;

import fitmatch_api.model.User;
import fitmatch_api.repository.UserRepository;
import fitmatch_api.security.AuthContext;
import fitmatch_api.security.JwtPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Endpoint para o app registrar o token FCM do dispositivo.
 *
 * SEGURANÇA: o userId é derivado do JWT (via AuthContext), NUNCA do corpo da
 * requisição. Dessa forma um usuário só consegue atualizar o PRÓPRIO token,
 * impossibilitando que um atacante vincule o token dele à conta de outra pessoa.
 */
@RestController
@RequestMapping("/users")
public class FcmTokenController {

    private static final int MAX_TOKEN_LENGTH = 512;

    private final UserRepository userRepository;

    public FcmTokenController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Registra (ou atualiza) o token FCM do usuário autenticado. */
    @PutMapping("/me/fcm-token")
    public Map<String, Object> updateFcmToken(@RequestBody(required = false) FcmTokenRequest body) {
        JwtPrincipal principal = AuthContext.requirePrincipal();

        String token = body == null ? null : body.fcmToken();
        if (token != null) {
            token = token.trim();
            if (token.length() > MAX_TOKEN_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fcmToken muito longo");
            }
        }

        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        user.setFcmToken((token == null || token.isEmpty()) ? null : token);
        userRepository.save(user);

        return Map.of("ok", true);
    }

    /** Remove o token (chamado no logout para parar de receber pushes). */
    @DeleteMapping("/me/fcm-token")
    public Map<String, Object> clearFcmToken() {
        JwtPrincipal principal = AuthContext.requirePrincipal();

        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        user.setFcmToken(null);
        userRepository.save(user);

        return Map.of("ok", true);
    }

    public record FcmTokenRequest(String fcmToken) {}
}
