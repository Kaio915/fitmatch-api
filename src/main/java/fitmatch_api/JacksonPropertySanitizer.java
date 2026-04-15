package fitmatch_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Remove propriedades Jackson inválidas no Spring Boot 4.x / Jackson 3.x,
 * pois SerializationFeature.WRITE_DATES_AS_TIMESTAMPS foi removido dessa versão.
 * Envolve os PropertySources que contêm a chave inválida com uma versão filtrada.
 */
public class JacksonPropertySanitizer implements EnvironmentPostProcessor {

    private static final List<String> INVALID_KEYS = List.of(
        "spring.jackson.serialization.write-dates-as-timestamps",
        "spring.jackson.serialization.write_dates_as_timestamps",
        "SPRING_JACKSON_SERIALIZATION_WRITE_DATES_AS_TIMESTAMPS"
    );

    private static final List<String> INVALID_KEYS_NORMALIZED = INVALID_KEYS.stream()
        .map(JacksonPropertySanitizer::normalizeKey)
        .toList();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources sources = environment.getPropertySources();
        List<PropertySource<?>> toReplace = new ArrayList<>();

        for (PropertySource<?> source : sources) {
            if (source instanceof FilteredPropertySource) {
                continue;
            }
            if (source instanceof EnumerablePropertySource<?>) {
                toReplace.add(source);
            }
        }

        for (PropertySource<?> source : toReplace) {
            sources.replace(source.getName(), new FilteredPropertySource((EnumerablePropertySource<?>) source));
        }
    }

    /** Envolve um PropertySource e exclui as chaves Jackson inválidas da enumeração e lookup. */
    private static class FilteredPropertySource extends EnumerablePropertySource<Object> {

        private final EnumerablePropertySource<?> delegate;

        FilteredPropertySource(EnumerablePropertySource<?> delegate) {
            super("filtered-" + delegate.getName(), new Object());
            this.delegate = delegate;
        }

        @Override
        public String[] getPropertyNames() {
            return Arrays.stream(delegate.getPropertyNames())
                    .filter(k -> !isInvalidKey(k))
                    .toArray(String[]::new);
        }

        @Override
        public Object getProperty(String name) {
            if (isInvalidKey(name)) return null;
            return delegate.getProperty(name);
        }

        @Override
        public boolean containsProperty(String name) {
            if (isInvalidKey(name)) return false;
            return delegate.containsProperty(name);
        }
    }

    private static boolean isInvalidKey(String key) {
        if (key == null || key.isBlank()) return false;
        String normalized = normalizeKey(key);
        return INVALID_KEYS_NORMALIZED.contains(normalized);
    }

    private static String normalizeKey(String key) {
        return key
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(".", "")
                .trim();
    }
}
