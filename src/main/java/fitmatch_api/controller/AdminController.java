package fitmatch_api.controller;

import fitmatch_api.model.User;
import fitmatch_api.model.UserStatus;
import fitmatch_api.model.UserType;
import fitmatch_api.repository.UserRepository;
import fitmatch_api.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

        private static final String ADMIN_DELETED_REASON = "Conta excluída pelo administrador";

    private final UserRepository repo;

    public AdminController(UserRepository repo) {
        this.repo = repo;
    }

    // ================= CPF MASK =================

    private static String maskCpf(String cpf) {

        if (cpf == null || cpf.length() != 11)
            return cpf;

        return cpf.substring(0, 3)
                + ".***.***-"
                + cpf.substring(9);
    }

    // ================= FOTO BASE64 =================

    private static String photoToBase64(byte[] photo) {

        if (photo == null || photo.length == 0)
            return null;

        return Base64.getEncoder().encodeToString(photo);
    }

    // ================= DTO =================

    public record AdminUserResponse(
            Long id,
            String name,
            String email,
            String type,
            String status,
            String cpf,
            String photoBase64,
            String objetivos,
            String nivel,
            String cref,
            String cidade,
            String especialidade,
            String experiencia,
            String valorHora,
            String bio,
            LocalDateTime createdAt,
            String rejectionReason
    ) {

        public static AdminUserResponse from(User u) {

            String reason = u.getRejectionReason();
            boolean deletedByAdmin = reason != null
                    && reason.trim().equalsIgnoreCase(ADMIN_DELETED_REASON);
            String statusLabel = deletedByAdmin
                    ? "DELETED"
                    : (u.getStatus() == null ? null : u.getStatus().name());

            return new AdminUserResponse(

                    u.getId(),

                    u.getName(),

                    u.getEmail(),

                    u.getType() == null
                            ? null
                            : u.getType().name().toLowerCase(),

                    statusLabel,

                    maskCpf(u.getCpf()),

                    photoToBase64(u.getPhoto()),

                    u.getObjetivos(),

                    u.getNivel(),

                    u.getCref(),

                    u.getCidade(),

                    u.getEspecialidade(),

                    u.getExperiencia(),

                    u.getValorHora(),

                    u.getBio(),

                    u.getCreatedAt(),

                    u.getRejectionReason()
            );
        }
    }

    // ================= PENDING =================

    @GetMapping("/pending/{type}")
    public List<AdminUserResponse> getPending(
            @PathVariable UserType type
    ) {

        AuthContext.requireRole("ADMIN");

        return repo
                .findByTypeAndStatus(type, UserStatus.PENDING)
                .stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    // ================= APPROVE =================

    @PutMapping("/approve/{id}")
    public void approve(@PathVariable Long id) {

        AuthContext.requireRole("ADMIN");

        User user = repo
                .findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Usuário não encontrado"
                        )
                );

        user.setStatus(UserStatus.APPROVED);

        user.setRejectionReason(null);

        repo.save(user);
    }

    // ================= REJECT =================

    @PutMapping("/reject/{id}")
    public void reject(
            @PathVariable Long id,
            @RequestBody(required = false)
            Map<String, String> body
    ) {

        AuthContext.requireRole("ADMIN");

        User user = repo
                .findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Usuário não encontrado"
                        )
                );

        String reason =
                body != null
                        ? body.get("reason")
                        : null;

        user.setStatus(UserStatus.REJECTED);

        user.setRejectionReason(reason);

        repo.save(user);
    }

        @DeleteMapping("/users/{id}")
        public void deleteUser(@PathVariable Long id) {

                AuthContext.requireRole("ADMIN");

                User user = repo
                                .findById(id)
                                .orElseThrow(() ->
                                                new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "Usuário não encontrado"
                                                )
                                );

                if (user.getType() == UserType.admin) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Não é permitido excluir conta de admin");
                }

                user.setStatus(UserStatus.REJECTED);
                user.setRejectionReason(ADMIN_DELETED_REASON);
                repo.save(user);
        }

    // ================= HISTORY =================

    @GetMapping("/users/{type}")
    public List<AdminUserResponse> getUsersHistory(
            @PathVariable UserType type,
            @RequestParam(required = false)
            UserStatus status
    ) {

        AuthContext.requireRole("ADMIN");

        List<User> users;

        if (status != null) {

            users =
                    repo.findByTypeAndStatus(
                            type,
                            status
                    );

        } else {

            users =
                    repo.findByTypeAndStatusIn(
                            type,
                            List.of(
                                    UserStatus.APPROVED,
                                    UserStatus.REJECTED
                            )
                    );
        }

        return users
                .stream()
                .map(AdminUserResponse::from)
                .toList();
    }

}