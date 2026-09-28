package fitmatch_api.repository;

import fitmatch_api.model.WorkoutFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface WorkoutFavoriteRepository extends JpaRepository<WorkoutFavorite, Long> {
    List<WorkoutFavorite> findByTrainerIdOrderByUpdatedAtDesc(Long trainerId);

    Optional<WorkoutFavorite> findByIdAndTrainerId(Long id, Long trainerId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WorkoutFavorite f where f.trainerId = :trainerId")
    int deleteByTrainerId(@Param("trainerId") Long trainerId);
}
