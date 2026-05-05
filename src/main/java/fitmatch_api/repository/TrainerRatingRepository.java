package fitmatch_api.repository;

import fitmatch_api.model.TrainerRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrainerRatingRepository extends JpaRepository<TrainerRating, Long> {
    List<TrainerRating> findByTrainerIdOrderByCreatedAtDesc(Long trainerId);
    Optional<TrainerRating> findByTrainerIdAndStudentId(Long trainerId, Long studentId);

    interface RatingSummary {
        Long getTrainerId();
        Double getAvgStars();
        Long getCount();
    }

    @Query("SELECT r.trainerId AS trainerId, AVG(r.stars) AS avgStars, COUNT(r.id) AS count " +
           "FROM TrainerRating r GROUP BY r.trainerId")
    List<RatingSummary> findRatingSummaries();
}
