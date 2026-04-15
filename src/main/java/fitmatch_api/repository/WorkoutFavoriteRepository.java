package fitmatch_api.repository;

import fitmatch_api.model.WorkoutFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutFavoriteRepository extends JpaRepository<WorkoutFavorite, Long> {
    List<WorkoutFavorite> findByTrainerIdOrderByUpdatedAtDesc(Long trainerId);

    Optional<WorkoutFavorite> findByIdAndTrainerId(Long id, Long trainerId);
}
