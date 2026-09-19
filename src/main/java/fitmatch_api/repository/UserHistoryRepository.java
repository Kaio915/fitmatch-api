package fitmatch_api.repository;

import fitmatch_api.model.UserHistory;
import fitmatch_api.model.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {

    List<UserHistory> findByTypeOrderByRecordedAtDesc(UserType type);

    List<UserHistory> findByTypeAndStatusOrderByRecordedAtDesc(UserType type, String status);

    boolean existsByUserId(Long userId);

    Optional<UserHistory> findTopByUserIdAndStatusOrderByRecordedAtDesc(Long userId, String status);

    // Usada para exibir a rejeição anterior quando o mesmo email se cadastra
    // novamente (o registro de histórico preserva o email do snapshot).
    Optional<UserHistory> findTopByEmailAndStatusOrderByRecordedAtDesc(String email, String status);

    // Último registro de qualquer status para um email — usado para verificar se
    // a rejeição ainda é o evento terminal mais recente (não foi superada por
    // uma aprovação/exclusão posterior).
    Optional<UserHistory> findTopByEmailOrderByRecordedAtDesc(String email);

    // Usada para exibir o aviso de "conta excluída anteriormente" quando o
    // mesmo email/cpf faz um novo cadastro.
    Optional<UserHistory> findTopByEmailAndDeletedOrderByRecordedAtDesc(String email, boolean deleted);

    // Todas as exclusões de conta de um email (deleted = true), da mais
    // recente para a mais antiga — usada para listar TODOS os motivos de
    // exclusão no chat, e não apenas o último.
    List<UserHistory> findByEmailAndDeletedOrderByRecordedAtDesc(String email, boolean deleted);

    // Registros de banimento de um usuário (usados no desbanir).
    List<UserHistory> findByUserIdAndBanned(Long userId, boolean banned);

    // Registros de banimento de um tipo de usuário (usados no "limpar só banidos").
    List<UserHistory> findByTypeAndBannedOrderByRecordedAtDesc(UserType type, boolean banned);

    // Aprovados "ativos" (não excluídos) — usados no "limpar só aprovados".
    @Query("SELECT h FROM UserHistory h WHERE h.type = :type AND h.status = 'APPROVED' AND h.deleted = false")
    List<UserHistory> findActiveApproved(@Param("type") UserType type);

    // Aprovados ativos de um usuário específico — usados na exclusão de conta
    // para marcar o histórico como "excluído" (deleted = true).
    @Query("SELECT h FROM UserHistory h WHERE h.userId = :userId AND h.status = 'APPROVED' AND h.deleted = false")
    List<UserHistory> findActiveApprovedByUserId(@Param("userId") Long userId);

    // Excluídos (legado DELETED ou aprovados que viraram excluídos).
    @Query("SELECT h FROM UserHistory h WHERE h.type = :type AND (h.status = 'DELETED' OR h.deleted = true)")
    List<UserHistory> findExcluded(@Param("type") UserType type);

    // Tudo exceto aprovados ativos — usado no "limpar histórico" para que
    // aprovados nunca sejam removidos do histórico (só via exclusão de conta).
    @Query("SELECT h FROM UserHistory h WHERE h.type = :type AND NOT (h.status = 'APPROVED' AND h.deleted = false)")
    List<UserHistory> findExcludingActiveApproved(@Param("type") UserType type);

    @Transactional
    void deleteByUserId(Long userId);

    // Exclui todos os registros de histórico de um usuário para um tipo
    // específico, mantendo o histórico do outro tipo (aluno/personal) separado.
    @Transactional
    void deleteByUserIdAndType(Long userId, UserType type);
}
