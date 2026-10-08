package fitmatch_api.dto;

import java.util.List;

public record AlimentoDTO(
        String name,
        double caloriesPer100g,
        double proteinPer100g,
        double carbsPer100g,
        double fatPer100g,
        String brand,
        String category,
        String source,
        String servingDescription,
        Double servingAmountGrams,
        String servingUnit,
        List<AlimentoServingDTO> servings
) {}
