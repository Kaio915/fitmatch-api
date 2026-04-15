package fitmatch_api.repository;

import fitmatch_api.model.DietFood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DietFoodRepository extends JpaRepository<DietFood, Long> {

    List<DietFood> findByUserIdOrderByNameAsc(Long userId);

    Optional<DietFood> findByIdAndUserId(Long id, Long userId);

    Optional<DietFood> findByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}
