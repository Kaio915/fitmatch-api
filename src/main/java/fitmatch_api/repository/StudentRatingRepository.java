package fitmatch_api.repository;

import fitmatch_api.model.StudentRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface StudentRatingRepository extends JpaRepository<StudentRating, Long> {
    List<StudentRating> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    Optional<StudentRating> findByTrainerIdAndStudentId(Long trainerId, Long studentId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from StudentRating r where r.trainerId = :trainerId")
    int deleteByTrainerId(@Param("trainerId") Long trainerId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from StudentRating r where r.studentId = :studentId")
    int deleteByStudentId(@Param("studentId") Long studentId);
}
