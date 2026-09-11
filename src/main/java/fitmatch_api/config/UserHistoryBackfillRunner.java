package fitmatch_api.config;

import fitmatch_api.model.User;
import fitmatch_api.model.UserHistory;
import fitmatch_api.model.UserStatus;
import fitmatch_api.model.UserType;
import fitmatch_api.repository.UserHistoryRepository;
import fitmatch_api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preenche o histórico de tentativas a partir dos usuários que já existiam
 * antes da criação da tabela "user_history". É idempotente: só cria um registro
 * para usuários que ainda não possuem nenhum registro de histórico.
 */
@Component
public class UserHistoryBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserHistoryBackfillRunner.class);
    private static final String ADMIN_DELETED_REASON = "Conta excluída pelo administrador";

    private final UserRepository userRepo;
    private final UserHistoryRepository historyRepo;

    public UserHistoryBackfillRunner(UserRepository userRepo, UserHistoryRepository historyRepo) {
        this.userRepo = userRepo;
        this.historyRepo = historyRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            int created = 0;

            for (User user : userRepo.findAll()) {
                if (user.getType() == UserType.admin) {
                    continue;
                }
                if (user.getStatus() != UserStatus.APPROVED
                        && user.getStatus() != UserStatus.REJECTED) {
                    continue;
                }
                if (historyRepo.existsByUserId(user.getId())) {
                    continue;
                }

                UserHistory h = new UserHistory();
                h.setUserId(user.getId());
                h.setName(user.getName());
                h.setEmail(user.getEmail());
                h.setCpf(user.getCpf());
                h.setType(user.getType());
                h.setStatus(terminalStatusOf(user));
                h.setRejectionReason(user.getRejectionReason());
                try {
                    h.setPhoto(user.getPhoto());
                } catch (RuntimeException e) {
                    h.setPhoto(null);
                }
                h.setObjetivos(user.getObjetivos());
                h.setNivel(user.getNivel());
                h.setCref(user.getCref());
                h.setCidade(user.getCidade());
                h.setEspecialidade(user.getEspecialidade());
                h.setExperiencia(user.getExperiencia());
                h.setValorHora(user.getValorHora());
                h.setBio(user.getBio());
                h.setCreatedAt(user.getCreatedAt());

                historyRepo.save(h);
                created++;
            }

            if (created > 0) {
                log.info("Backfill do histórico de cadastros: {} registro(s) criado(s).", created);
            }
        } catch (Exception e) {
            log.warn("Falha ao executar o backfill do histórico de cadastros: {}", e.getMessage());
        }
    }

    private static String terminalStatusOf(User user) {
        String reason = user.getRejectionReason();
        if (user.getStatus() == UserStatus.APPROVED) {
            return "APPROVED";
        }
        if (reason != null && reason.trim().equalsIgnoreCase(ADMIN_DELETED_REASON)) {
            return "DELETED";
        }
        return "REJECTED";
    }
}
