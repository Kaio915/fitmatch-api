package fitmatch_api.service;

import fitmatch_api.dto.AlimentoDTO;
import fitmatch_api.model.TacoFood;
import fitmatch_api.repository.TacoFoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Busca híbrida de alimentos: primeiro consulta a base local (TACO) via
 * ILIKE na descrição e, em seguida, concatena os resultados da API do
 * FatSecret. Cada resultado carrega a flag {@code source} ("TACO" ou
 * "FATSECRET").
 */
@Service
public class FoodSearchService {

    private static final Logger log = LoggerFactory.getLogger(FoodSearchService.class);

    private final TacoFoodRepository tacoFoodRepository;
    private final FatSecretService fatSecretService;

    public FoodSearchService(TacoFoodRepository tacoFoodRepository, FatSecretService fatSecretService) {
        this.tacoFoodRepository = tacoFoodRepository;
        this.fatSecretService = fatSecretService;
    }

    public List<AlimentoDTO> search(String termo) {
        String term = termo == null ? "" : termo.trim();
        if (term.length() < 2) {
            return List.of();
        }

        List<AlimentoDTO> results = new ArrayList<>();

        List<TacoFood> tacoFoods = tacoFoodRepository.searchByDescription(term);
        for (TacoFood food : tacoFoods) {
            results.add(toAlimentoDTO(food));
        }
        log.info("Busca híbrida: {} alimento(s) da TACO para o termo '{}'.", tacoFoods.size(), term);

        results.addAll(fatSecretService.searchFoods(term));

        return results;
    }

    private AlimentoDTO toAlimentoDTO(TacoFood food) {
        // A TACO descreve a composição sempre por 100 g (base_qty), ou seja, não
        // possui um "peso de porção/unidade" oficial. Por isso `servingAmountGrams`
        // e `defaultServingGrams` ficam nulos: assim, unidades do tipo "unidade(s)",
        // "porção" e "fatia(s)" caem no fallback seguro (50 g) no cálculo, em vez de
        // multiplicar por 100 g e inflar as calorias.
        return new AlimentoDTO(
                food.getDescription(),
                round1(val(food.getCalories())),
                round1(val(food.getProtein())),
                round1(val(food.getCarbs())),
                round1(val(food.getLipids())),
                null,
                null,
                "TACO",
                "100 g",
                null,
                "g",
                List.of(),
                null
        );
    }

    private static double val(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
