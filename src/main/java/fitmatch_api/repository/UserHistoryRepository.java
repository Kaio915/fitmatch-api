package fitmatch_api.repository;

import fitmatch_api.model.UserHistory;
import fitmatch_api.model.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Histórico visível na tela (não "limpo").
    List<UserHistory> findByTypeAndHiddenOrderByRecordedAtDesc(UserType type, boolean hidden);

    List<UserHistory> findByTypeAndStatusAndHiddenOrderByRecordedAtDesc(UserType type, String status, boolean hidden);

    // Banidos visíveis na tela (não "limpos" pelo admin).
    List<UserHistory> findByTypeAndBannedAndHiddenOrderByRecordedAtDesc(UserType type, boolean banned, boolean hidden);

    // Todas as rejeições de um email (mesmo que já tenham sido "limpas" da tela).
    List<UserHistory> findByEmailAndStatusOrderByRecordedAtDesc(String email, String status);

    // Último evento terminal que NÃO é uma rejeição (aprovação/exclusão), usado
    // para descartar rejeições antigas superadas por uma aprovação/exclusão.
    @Query("SELECT h FROM UserHistory h WHERE h.email = :email AND h.status <> 'REJECTED' ORDER BY h.recordedAt DESC")
    Optional<UserHistory> findLatestNonRejection(@Param("email") String email);

    // Todas as exclusões de um email (deleted = true ou status legado DELETED).
    @Query("SELECT h FROM UserHistory h WHERE h.email = :email AND (h.deleted = true OR h.status = 'DELETED') ORDER BY h.recordedAt DESC")
    List<UserHistory> findExclusionsByEmail(@Param("email") String email);

    // Rejeições "normais" de um email (exclui os registros de banimento, que têm
    // bannedReason preenchido) — usadas no "previous-rejection".
    @Query("SELECT h FROM UserHistory h WHERE h.email = :email AND h.status = 'REJECTED' AND h.bannedReason IS NULL ORDER BY h.recordedAt DESC")
    List<UserHistory> findRejectionsByEmail(@Param("email") String email);

    // Banimentos de um email (bannedReason preenchido) — usados no "previous-ban".
    @Query("SELECT h FROM UserHistory h WHERE h.email = :email AND h.bannedReason IS NOT NULL ORDER BY h.recordedAt DESC")
    List<UserHistory> findBansByEmail(@Param("email") String email);

    // Todos os registros de histórico de um usuário — usados para ocultar
    // (hidden = true) em vez de apagar, preservando o motivo da rejeição/exclusão
    // que é exibido no chat quando o mesmo email/cpf se cadastra novamente.
    List<UserHistory> findByUserId(Long userId);

    // Todos os registros de histórico de um usuário para um tipo específico
    // (aluno OU personal), usados para ocultar em vez de apagar, mantendo o
    // histórico do outro tipo separado.
    List<UserHistory> findByUserIdAndType(Long userId, UserType type);
}
