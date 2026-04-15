package fitmatch_api.repository;

import fitmatch_api.model.DietEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DietEntryRepository extends JpaRepository<DietEntry, Long> {

    List<DietEntry> findByUserIdAndEntryDateOrderByCreatedAtAsc(Long userId, LocalDate entryDate);

    Optional<DietEntry> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndEntryDateAndMealTypeIgnoreCase(Long userId, LocalDate entryDate, String mealType);

    boolean existsByUserIdAndFoodId(Long userId, Long foodId);
}
