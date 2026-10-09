package fitmatch_api.dto;

/**
 * Uma porção real retornada pela FatSecret (array "servings").
 * Cada porção tem sua própria descrição, peso em gramas e macros.
 */
public record AlimentoServingDTO(
        String description,
        Double amountGrams,
        String unit,
        Double calories,
        Double protein,
        Double carbs,
        Double fat,
        Boolean isDefault
) {}
