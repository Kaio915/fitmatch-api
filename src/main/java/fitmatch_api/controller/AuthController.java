package fitmatch_api.controller;

import fitmatch_api.model.User;
import fitmatch_api.model.UserStatus;
import fitmatch_api.model.UserType;
import fitmatch_api.service.CrefValidationService;
import fitmatch_api.repository.BlockedStudentRepository;
import fitmatch_api.repository.TrainerRatingRepository;
import fitmatch_api.repository.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@CrossOrigin
public class AuthController {

    private static final String ADMIN_DELETED_REASON = "Conta excluída pelo administrador";

    private final UserRepository repo;
    private final TrainerRatingRepository ratingRepo;
    private final BlockedStudentRepository blockedStudentRepo;
    private final CrefValidationService crefValidationService;

    public AuthController(
            UserRepository repo,
            TrainerRatingRepository ratingRepo,
            BlockedStudentRepository blockedStudentRepo,
            CrefValidationService crefValidationService
    ) {
        this.repo = repo;
        this.ratingRepo = ratingRepo;
        this.blockedStudentRepo = blockedStudentRepo;
        this.crefValidationService = crefValidationService;
    }

    // ================= LOGIN (JSON) =================
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {

        final String reqEmail = (request.email() == null) ? "" : request.email().trim();
        final String reqPass  = (request.password() == null) ? "" : request.password().trim();
        final String reqType  = (request.type() == null) ? "" : request.type().trim().toLowerCase();

        if (reqEmail.isEmpty() || reqPass.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preencha email e senha");
        }

        User user = repo.findByEmail(reqEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Usuário não encontrado"
                ));

        if (user.getPassword() == null || !user.getPassword().equals(reqPass)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha inválida");
        }

        // ✅ ADMIN: sempre pode logar em qualquer tela
        if (user.getType() == UserType.admin) {
            return AuthResponse.from(user);
        }

        // ✅ aluno/personal: precisa logar pela tela correta
        UserType requestedType;
        try {
            requestedType = UserType.valueOf(reqType);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inválido");
        }

        if (user.getType() != requestedType) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Você deve entrar pela tela de " + user.getType().name().toLowerCase()
            );
        }

        if (user.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cadastro inválido (status nulo)");
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cadastro em análise");
        }

        if (user.getStatus() == UserStatus.REJECTED) {
            String reason = user.getRejectionReason();
            if (reason != null && reason.trim().equalsIgnoreCase(ADMIN_DELETED_REASON)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta excluída. Acesso desativado.");
            }
            String msg = (reason == null || reason.trim().isEmpty())
                    ? "Cadastro rejeitado"
                    : "Cadastro rejeitado: " + reason.trim();
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
        }

        if (user.getStatus() == UserStatus.APPROVED) {
            return AuthResponse.from(user);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status inválido");
    }

    // ================= REGISTER STUDENT (MULTIPART) =================
    @PostMapping(value = "/register/student", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void registerStudent(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String cpf,
            @RequestParam String objetivos,
            @RequestParam String nivel,
            @RequestPart("photo") MultipartFile photo
    ) {
        final String emailNorm = safe(email);
        final String cpfNorm = normalizeCpf(cpf);

        if (emailNorm.isEmpty() || safe(password).isEmpty() || safe(name).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preencha nome, email e senha");
        }
        if (repo.findByEmail(emailNorm).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email já cadastrado");
        }
        if (repo.findByCpf(cpfNorm).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF já cadastrado");
        }

        byte[] photoBytes = readPhoto(photo);

        User user = new User();
        user.setName(safe(name));
        user.setEmail(emailNorm);
        user.setPassword(safe(password));
        user.setCpf(cpfNorm);

        user.setType(UserType.aluno);
        user.setStatus(UserStatus.PENDING);

        user.setObjetivos(safe(objetivos));
        user.setNivel(safe(nivel));

        user.setPhoto(photoBytes);

        repo.save(user);
    }

    // ================= REGISTER TRAINER (MULTIPART) =================
    @PostMapping(value = "/register/trainer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void registerTrainer(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String cpf,
            @RequestParam String cref,
            @RequestParam String cidade,
            @RequestParam(required = false) String especialidade,
            @RequestParam(required = false) String valorHora,
            @RequestParam String bio,
            @RequestPart("photo") MultipartFile photo
    ) {
        final String emailNorm = safe(email);
        final String cpfNorm = normalizeCpf(cpf);
        final CrefValidationService.CrefValidationResult crefValidation =
                crefValidationService.validate(cref, null);

        if (emailNorm.isEmpty() || safe(password).isEmpty() || safe(name).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preencha nome, email e senha");
        }
        if (!crefValidation.formatValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CREF inválido: " + crefValidation.formatMessage());
        }
        if (repo.findByEmail(emailNorm).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email já cadastrado");
        }
        if (repo.findByCpf(cpfNorm).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF já cadastrado");
        }

        byte[] photoBytes = readPhoto(photo);

        User user = new User();
        user.setName(safe(name));
        user.setEmail(emailNorm);
        user.setPassword(safe(password));
        user.setCpf(cpfNorm);

        user.setType(UserType.personal);
        user.setStatus(UserStatus.PENDING);

        user.setCref(crefValidation.normalizedCref());
        user.setCidade(safe(cidade));
        user.setEspecialidade(safe(especialidade));
        user.setValorHora(safe(valorHora));
        user.setBio(safe(bio));

        user.setPhoto(photoBytes);

        repo.save(user);
    }

    // ================= HELPERS =================
    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static String normalizeCpf(String cpf) {
        String onlyDigits = safe(cpf).replaceAll("[^0-9]", "");
        if (onlyDigits.length() != 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF inválido");
        }
        return onlyDigits;
    }

    private static byte[] readPhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie a foto");
        }

        // valida tipo (jpg/png)
        String ct = photo.getContentType();
        if (ct == null || (!ct.equals("image/jpeg") && !ct.equals("image/png"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Foto deve ser JPG ou PNG");
        }

        // limite simples (2MB)
        if (photo.getSize() > 2 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem muito grande (máx 2MB)");
        }

        try {
            return photo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falha ao ler a imagem");
        }
    }

    // ================= DTOs =================
    public record LoginRequest(String email, String password, String type) {}

    public record AuthResponse(
            Long id,
            String name,
            String email,
            String type,
            String status,
            String cpf,
            String rejectionReason,
            // Campos do aluno (null para personal/admin)
            String objetivos,
            String nivel,
            // Campos do personal trainer (null para alunos/admin)
            String cref,
            String cidade,
            String especialidade,
            String valorHora,
            String horasPorSessao,
            String bio
    ) {
        public static AuthResponse from(User u) {
            return new AuthResponse(
                    u.getId(),
                    u.getName(),
                    u.getEmail(),
                    u.getType() == null ? null : u.getType().name().toLowerCase(),
                    u.getStatus() == null ? null : u.getStatus().name(),
                    u.getCpf(),
                    u.getRejectionReason(),
                    u.getObjetivos(),
                    u.getNivel(),
                    u.getCref(),
                    u.getCidade(),
                    u.getEspecialidade(),
                    u.getValorHora(),
                    u.getHorasPorSessao(),
                    u.getBio()
            );
        }
    }

    // ================= UPDATE TRAINER PROFILE =================
    public record UpdateTrainerProfileDto(String cidade, String valorHora, String horasPorSessao) {}

    @PatchMapping("/trainer/{id}/profile")
    public void updateTrainerProfile(@PathVariable Long id, @RequestBody UpdateTrainerProfileDto dto) {
        User user = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        if (user.getType() != UserType.personal) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas personals podem atualizar o perfil");
        }
        if (dto.cidade() != null) user.setCidade(dto.cidade().trim());
        if (dto.valorHora() != null) user.setValorHora(dto.valorHora().trim());
        if (dto.horasPorSessao() != null) user.setHorasPorSessao(dto.horasPorSessao().trim());
        repo.save(user);
    }

    // ================= LIST APPROVED TRAINERS =================

    public record TrainerPublicInfo(
            Long id,
            String name,
            String cidade,
            String especialidade,
            String valorHora,
            String horasPorSessao,
            String bio,
            String cref,
            Double mediaAvaliacao,
            Integer totalAvaliacoes
    ) {}

        public record StudentPublicInfo(
            Long id,
            String name,
            String objetivos,
            String nivel
        ) {}

    // ================= GET USER BY ID (perfil público) =================

    public record UserPublicDto(
            Long id,
            String name,
            String type,
            String objetivos,
            String nivel,
            String cidade,
            String especialidade,
            String bio,
            String cref,
            String valorHora,
            String horasPorSessao
    ) {}

    @GetMapping("/user/{id}")
    public UserPublicDto getUserById(@PathVariable Long id) {
        User user = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        return new UserPublicDto(
                user.getId(),
                user.getName(),
                user.getType() != null ? user.getType().toString() : null,
                user.getObjetivos(),
                user.getNivel(),
                user.getCidade(),
                user.getEspecialidade(),
                user.getBio(),
                user.getCref(),
                user.getValorHora(),
                user.getHorasPorSessao()
        );
    }

    @GetMapping("/user/{id}/photo")
    public ResponseEntity<byte[]> getUserPhoto(@PathVariable Long id) {
        User user = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        byte[] photo = user.getPhoto();
        if (photo == null || photo.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto não encontrada");
        }

        MediaType contentType = detectImageContentType(photo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0")
                .contentType(contentType)
                .body(photo);
    }

    private MediaType detectImageContentType(byte[] imageBytes) {
        if (imageBytes.length >= 8
                && (imageBytes[0] & 0xFF) == 0x89
                && imageBytes[1] == 0x50
                && imageBytes[2] == 0x4E
                && imageBytes[3] == 0x47) {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.IMAGE_JPEG;
    }

    // ================= LIST APPROVED TRAINERS =================

    @GetMapping("/trainers")
    public java.util.List<TrainerPublicInfo> listApprovedTrainers(@RequestParam(required = false) Long studentId) {
        Set<Long> blockedTrainerIds = studentId == null
                ? java.util.Collections.emptySet()
                : blockedStudentRepo.findByStudentId(studentId)
                    .stream()
                    .map(block -> block.getTrainerId())
                    .collect(Collectors.toSet());

        return repo.findByTypeAndStatus(UserType.personal, UserStatus.APPROVED)
                .stream()
                .filter(u -> !blockedTrainerIds.contains(u.getId()))
                .map(u -> {
                    var ratings = ratingRepo.findByTrainerIdOrderByCreatedAtDesc(u.getId());
                    double media = ratings.isEmpty() ? 0.0 :
                            ratings.stream().mapToInt(r -> r.getStars()).average().orElse(0.0);
                    double mediaRounded = (double) Math.round(media * 10) / 10.0;
                    return new TrainerPublicInfo(
                            u.getId(),
                            u.getName(),
                            u.getCidade(),
                            u.getEspecialidade(),
                            u.getValorHora(),
                            u.getHorasPorSessao(),
                            u.getBio(),
                            u.getCref(),
                            ratings.isEmpty() ? null : mediaRounded,
                            ratings.size()
                    );
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/students")
    public java.util.List<StudentPublicInfo> listApprovedStudents() {
        return repo.findByTypeAndStatus(UserType.aluno, UserStatus.APPROVED)
                .stream()
                .map(u -> new StudentPublicInfo(
                        u.getId(),
                        u.getName(),
                        u.getObjetivos(),
                        u.getNivel()
                ))
                .collect(java.util.stream.Collectors.toList());
    }
}