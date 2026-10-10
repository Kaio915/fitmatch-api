package fitmatch_api.service;

import fitmatch_api.dto.AlimentoDTO;
import fitmatch_api.dto.AlimentoServingDTO;
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
        // A TACO descreve a composição sempre por 100 g (base_qty). A medida caseira
        // oficial é "100 g", portanto a porção real tem peso de 100 g — sem nenhum
        // chute de peso. Assim, "g" e "ml" seguem 1:1 e a porção padrão usa 100 g.
        double baseQty = food.getBaseQty() != null && food.getBaseQty() > 0 ? food.getBaseQty() : 100.0;
        String servingDescription = formatTacoServing(baseQty) + " g";
        AlimentoServingDTO serving = new AlimentoServingDTO(
                servingDescription,
                baseQty,
                "g",
                null,
                null,
                null,
                null,
                true
        );
        return new AlimentoDTO(
                food.getDescription(),
                round1(val(food.getCalories())),
                round1(val(food.getProtein())),
                round1(val(food.getCarbs())),
                round1(val(food.getLipids())),
                null,
                null,
                "TACO",
                servingDescription,
                baseQty,
                "g",
                List.of(serving),
                baseQty
        );
    }

    private static String formatTacoServing(double baseQty) {
        if (baseQty == Math.rint(baseQty)) {
            return String.valueOf((long) baseQty);
        }
        return String.valueOf(baseQty);
    }

    private static double val(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
