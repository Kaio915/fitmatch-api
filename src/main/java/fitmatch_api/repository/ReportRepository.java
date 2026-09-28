package fitmatch_api.repository;

import fitmatch_api.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByReportedUserIdOrderByCreatedAtDesc(Long reportedUserId);

    long countBySeenFalse();

    // Número de USUÁRIOS distintos que possuem ao menos uma denúncia não vista
    // e ainda em aberto (não liberada).
    @Query("SELECT COUNT(DISTINCT r.reportedUserId) FROM Report r WHERE r.seen = false AND r.resolvedAt IS NULL")
    long countDistinctReportedUsersUnseen();

    // Marca as denúncias em aberto como vistas (zera a contagem de "novos").
    @Modifying
    @Transactional
    @Query("UPDATE Report r SET r.seen = true WHERE r.seen = false AND r.resolvedAt IS NULL")
    int markAllSeen();

    // Marca como "liberadas" todas as denúncias em aberto de um usuário.
    @Modifying
    @Transactional
    @Query("UPDATE Report r SET r.resolvedAt = :when WHERE r.reportedUserId = :userId AND r.resolvedAt IS NULL")
    int markResolvedByReportedUserId(@Param("userId") Long userId, @Param("when") LocalDateTime when);

    // Remove as denúncias de um usuário (ao excluir a conta/usuário).
    @Modifying
    @Transactional
    @Query("DELETE FROM Report r WHERE r.reportedUserId = :userId")
    int deleteByReportedUserId(@Param("userId") Long userId);

    // Remove as denúncias FEITAS por um usuário (ao resetar a conta).
    @Modifying
    @Transactional
    @Query("DELETE FROM Report r WHERE r.reporterId = :userId")
    int deleteByReporterId(@Param("userId") Long userId);
}
