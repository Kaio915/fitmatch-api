package fitmatch_api.repository;

import fitmatch_api.model.TacoFood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TacoFoodRepository extends JpaRepository<TacoFood, Long> {

    /**
     * Busca case-insensitive na descrição do alimento usando ILIKE (PostgreSQL).
     */
    @Query(
            value = "select * from taco_foods where description ilike '%' || :termo || '%' order by description",
            nativeQuery = true
    )
    List<TacoFood> searchByDescription(@Param("termo") String termo);
}
