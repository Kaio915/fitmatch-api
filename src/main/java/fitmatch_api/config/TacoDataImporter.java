package fitmatch_api.config;

import fitmatch_api.model.TacoFood;
import fitmatch_api.repository.TacoFoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Importa a Tabela TACO (Tabela Brasileira de Composição de Alimentos) a
 * partir do arquivo {@code data/taco.json} durante a inicialização da
 * aplicação. É idempotente: só importa quando a tabela {@code taco_foods}
 * está vazia, evitando duplicatas em reinicializações.
 */
@Component
public class TacoDataImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TacoDataImporter.class);

    private final TacoFoodRepository repository;
    private final ObjectMapper objectMapper;

    public TacoDataImporter(TacoFoodRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            long existing = repository.count();
            if (existing > 0) {
                log.info("TACO: tabela já populada ({} alimento(s)). Importação ignorada.", existing);
                return;
            }

            ClassPathResource resource = new ClassPathResource("data/taco.json");
            if (!resource.exists()) {
                log.warn("TACO: arquivo data/taco.json não encontrado no classpath. Importação ignorada.");
                return;
            }

            JsonNode root;
            try (InputStream in = resource.getInputStream()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                root = objectMapper.readTree(json);
            }

            if (root == null || !root.isArray()) {
                log.warn("TACO: o arquivo data/taco.json não contém um array JSON válido.");
                return;
            }

            List<TacoFood> foods = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            for (JsonNode node : root) {
                String description = textOf(node.path("description"));
                if (description.isBlank()) {
                    continue;
                }
                String key = description.toLowerCase();
                if (!seen.add(key)) {
                    continue;
                }

                JsonNode attributes = node.path("attributes");

                TacoFood food = new TacoFood();
                food.setTacoId(node.path("id").isNumber() ? node.path("id").asInt() : null);
                food.setDescription(description.trim());
                food.setBaseQty(doubleOrZero(node.path("base_qty")));
                food.setCalories(doubleOrZero(attributes.path("energy").path("kcal")));
                food.setProtein(doubleOrZero(attributes.path("protein").path("qty")));
                food.setCarbs(doubleOrZero(attributes.path("carbohydrate").path("qty")));
                food.setLipids(doubleOrZero(attributes.path("lipid").path("qty")));
                foods.add(food);
            }

            if (foods.isEmpty()) {
                log.warn("TACO: nenhum alimento válido encontrado no data/taco.json.");
                return;
            }

            repository.saveAll(foods);
            log.info("TACO: {} alimento(s) importado(s) com sucesso.", foods.size());
        } catch (Exception e) {
            log.warn("Falha ao importar a Tabela TACO: {}", e.getMessage(), e);
        }
    }

    private static String textOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText().trim();
    }

    private static double doubleOrZero(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0.0;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        String text = node.asText().trim();
        if (text.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
