package fitmatch_api.repository;

import fitmatch_api.model.TrainerRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainerRatingRepository extends JpaRepository<TrainerRating, Long> {
    List<TrainerRating> findByTrainerIdOrderByCreatedAtDesc(Long trainerId);
    Optional<TrainerRating> findByTrainerIdAndStudentId(Long trainerId, Long studentId);
}
