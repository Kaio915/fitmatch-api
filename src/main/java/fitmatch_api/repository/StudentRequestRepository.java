package fitmatch_api.repository;

import fitmatch_api.model.StudentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentRequestRepository extends JpaRepository<StudentRequest, Long> {
    @Query("SELECT r FROM StudentRequest r WHERE r.trainerId = :trainerId AND r.status = :status AND COALESCE(r.hiddenForTrainer, false) = false ORDER BY r.createdAt DESC")
    List<StudentRequest> findByTrainerIdAndStatusOrderByCreatedAtDesc(@Param("trainerId") Long trainerId,
                                                                       @Param("status") String status);

    List<StudentRequest> findByTrainerIdAndStatus(Long trainerId, String status);

    @Query("SELECT r FROM StudentRequest r WHERE r.trainerId = :trainerId AND (COALESCE(r.hiddenForTrainer, false) = false OR r.status = 'APPROVED') ORDER BY r.createdAt DESC")
    List<StudentRequest> findByTrainerIdOrderByCreatedAtDesc(@Param("trainerId") Long trainerId);

    List<StudentRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    // Verifica se aluno já tem solicitação aberta com um personal
    Optional<StudentRequest> findByStudentIdAndTrainerIdAndStatus(Long studentId, Long trainerId, String status);

    boolean existsByStudentIdAndTrainerIdAndStatus(Long studentId, Long trainerId, String status);

        // Última solicitação aprovada entre aluno e personal
        Optional<StudentRequest> findTopByStudentIdAndTrainerIdAndStatusOrderByCreatedAtDesc(
            Long studentId,
            Long trainerId,
            String status
        );

            List<StudentRequest> findByStudentIdAndTrainerIdAndStatusOrderByCreatedAtDesc(
                Long studentId,
                Long trainerId,
                String status
            );

                List<StudentRequest> findByStudentIdAndTrainerIdOrderByCreatedAtDesc(
                    Long studentId,
                    Long trainerId
                );

    // Verifica se aluno já tem QUALQUER solicitação no mesmo dia+horário (com qualquer personal)
    @Query("SELECT r FROM StudentRequest r WHERE r.studentId = :studentId AND r.dayName = :dayName AND r.time = :time AND r.status != 'REJECTED'")
    List<StudentRequest> findConflictingRequests(@Param("studentId") Long studentId,
                                                  @Param("dayName") String dayName,
                                                  @Param("time") String time);
}
