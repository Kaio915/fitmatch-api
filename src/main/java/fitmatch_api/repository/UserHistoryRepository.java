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

    // Aprovados "ativos" (não excluídos) — usados no "limpar só aprovados".
    @Query("SELECT h FROM UserHistory h WHERE h.type = :type AND h.status = 'APPROVED' AND h.deleted = false")
    List<UserHistory> findActiveApproved(@Param("type") UserType type);

    // Excluídos (legado DELETED ou aprovados que viraram excluídos).
    @Query("SELECT h FROM UserHistory h WHERE h.type = :type AND (h.status = 'DELETED' OR h.deleted = true)")
    List<UserHistory> findExcluded(@Param("type") UserType type);

    @Transactional
    void deleteByUserId(Long userId);
}
