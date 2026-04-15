package fitmatch_api.repository;

import fitmatch_api.model.StudentRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRatingRepository extends JpaRepository<StudentRating, Long> {
    List<StudentRating> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    Optional<StudentRating> findByTrainerIdAndStudentId(Long trainerId, Long studentId);
}
