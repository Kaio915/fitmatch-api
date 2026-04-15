package fitmatch_api.repository;

import fitmatch_api.model.DietGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DietGoalRepository extends JpaRepository<DietGoal, Long> {

    Optional<DietGoal> findByUserId(Long userId);
}
