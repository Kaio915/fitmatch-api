package fitmatch_api.repository;

import fitmatch_api.model.WorkoutCustomExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutCustomExerciseRepository extends JpaRepository<WorkoutCustomExercise, Long> {
    List<WorkoutCustomExercise> findByTrainerIdOrderByUpdatedAtDesc(Long trainerId);

    Optional<WorkoutCustomExercise> findByIdAndTrainerId(Long id, Long trainerId);

    boolean existsByTrainerIdAndNameIgnoreCase(Long trainerId, String name);
}
