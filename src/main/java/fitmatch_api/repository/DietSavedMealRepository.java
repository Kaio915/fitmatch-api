package fitmatch_api.repository;

import fitmatch_api.model.DietSavedMeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DietSavedMealRepository extends JpaRepository<DietSavedMeal, Long> {

    Optional<DietSavedMeal> findByUserIdAndMealTypeIgnoreCase(Long userId, String mealType);

    Optional<DietSavedMeal> findByIdAndUserId(Long id, Long userId);

    List<DietSavedMeal> findByUserIdOrderByMealTypeAsc(Long userId);

    List<DietSavedMeal> findByUserIdOrderByUpdatedAtDesc(Long userId);

    void deleteByUserIdAndMealTypeIgnoreCase(Long userId, String mealType);
}
