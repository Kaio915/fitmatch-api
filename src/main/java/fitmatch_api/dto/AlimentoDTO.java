package fitmatch_api.dto;

public record AlimentoDTO(
        String name,
        double caloriesPer100g,
        double proteinPer100g,
        double carbsPer100g,
        double fatPer100g,
        String brand,
        String category,
        String source
) {}
