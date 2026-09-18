package fitmatch_api.repository;

import fitmatch_api.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByReportedUserIdOrderByCreatedAtDesc(Long reportedUserId);

    long countBySeenFalse();

    // Número de USUÁRIOS distintos que possuem ao menos uma denúncia não vista.
    @Query("SELECT COUNT(DISTINCT r.reportedUserId) FROM Report r WHERE r.seen = false")
    long countDistinctReportedUsersUnseen();

    // Marca todas as denúncias como vistas (zera a contagem de "novos").
    @Modifying
    @Transactional
    @Query("UPDATE Report r SET r.seen = true WHERE r.seen = false")
    int markAllSeen();

    // Remove as denúncias de um usuário (ao excluir a conta/usuário).
    @Modifying
    @Transactional
    @Query("DELETE FROM Report r WHERE r.reportedUserId = :userId")
    int deleteByReportedUserId(@Param("userId") Long userId);
}
