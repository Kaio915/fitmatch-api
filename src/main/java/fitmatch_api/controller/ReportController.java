package fitmatch_api.controller;

import fitmatch_api.model.Report;
import fitmatch_api.model.User;
import fitmatch_api.model.UserType;
import fitmatch_api.repository.ReportRepository;
import fitmatch_api.repository.UserRepository;
import fitmatch_api.security.AuthContext;
import fitmatch_api.security.JwtPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Permite que um aluno ou personal denuncie o outro ao admin.
 *
 * A denúncia só pode ser feita pelo próprio usuário logado (o {@code reporterId}
 * precisa coincidir com o usuário autenticado) e o alvo não pode ser um admin.
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportRepository reportRepo;
    private final UserRepository userRepo;

    public ReportController(ReportRepository reportRepo, UserRepository userRepo) {
        this.reportRepo = reportRepo;
        this.userRepo = userRepo;
    }

    @PostMapping
    public Report create(@RequestBody ReportRequest body) {
        Long reporterId = body.reporterId();
        Long reportedUserId = body.reportedUserId();

        if (reporterId == null || reportedUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "reporterId e reportedUserId são obrigatórios"
            );
        }

        if (reporterId.equals(reportedUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Você não pode denunciar a si mesmo"
            );
        }

        // Apenas o próprio usuário logado pode registrar a denúncia em seu nome.
        JwtPrincipal principal = AuthContext.requirePrincipal();
        if (principal.userId() == null || !principal.userId().equals(reporterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }

        User reporter = userRepo.findById(reporterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuário denunciante não encontrado"));
        User reported = userRepo.findById(reportedUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuário denunciado não encontrado"));

        if (reporter.getType() == UserType.admin || reported.getType() == UserType.admin) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Não é possível denunciar um administrador"
            );
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setReportedUserId(reportedUserId);
        report.setReason(body.reason() == null || body.reason().isBlank()
                ? null
                : body.reason().trim());
        report.setDetails(body.details() == null || body.details().isBlank()
                ? null
                : body.details().trim());
        report.setSeen(false);

        return reportRepo.save(report);
    }

    public record ReportRequest(
            Long reporterId,
            Long reportedUserId,
            String reason,
            String details) {}
}
