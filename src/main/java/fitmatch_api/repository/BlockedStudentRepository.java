package fitmatch_api.repository;

import fitmatch_api.model.BlockedStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BlockedStudentRepository extends JpaRepository<BlockedStudent, Long> {
    Optional<BlockedStudent> findByTrainerIdAndStudentId(Long trainerId, Long studentId);
    boolean existsByTrainerIdAndStudentId(Long trainerId, Long studentId);
    List<BlockedStudent> findByTrainerIdOrderByBlockedAtDesc(Long trainerId);
    List<BlockedStudent> findByStudentId(Long studentId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from BlockedStudent b where b.trainerId = :trainerId and b.studentId = :studentId")
    int deleteByTrainerIdAndStudentId(@Param("trainerId") Long trainerId, @Param("studentId") Long studentId);
}
