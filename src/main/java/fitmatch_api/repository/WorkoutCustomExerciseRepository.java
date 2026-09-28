package fitmatch_api.repository;

import fitmatch_api.model.WorkoutCustomExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface WorkoutCustomExerciseRepository extends JpaRepository<WorkoutCustomExercise, Long> {
    List<WorkoutCustomExercise> findByTrainerIdOrderByUpdatedAtDesc(Long trainerId);

    Optional<WorkoutCustomExercise> findByIdAndTrainerId(Long id, Long trainerId);

    boolean existsByTrainerIdAndNameIgnoreCase(Long trainerId, String name);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WorkoutCustomExercise e where e.trainerId = :trainerId")
    int deleteByTrainerId(@Param("trainerId") Long trainerId);
}
