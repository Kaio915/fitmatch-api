package fitmatch_api.repository;

import fitmatch_api.model.DietGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface DietGoalRepository extends JpaRepository<DietGoal, Long> {

    Optional<DietGoal> findByUserId(Long userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DietGoal g where g.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
