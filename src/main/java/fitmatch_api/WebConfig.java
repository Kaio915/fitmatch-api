package fitmatch_api;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class WebConfig {

    @Value("${app.cors.allowed-origins:https://fitmatch.page,https://www.fitmatch.page,http://localhost:*,http://127.0.0.1:*,http://[::1]:*}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers:Authorization,Content-Type,Accept,Origin,X-Requested-With}")
    private String allowedHeaders;

    @Value("${app.cors.exposed-headers:Authorization,Location,Content-Disposition}")
    private String exposedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = splitCsv(allowedOrigins);
        if (origins.isEmpty()) {
            config.addAllowedOriginPattern("https://fitmatch.page");
            config.addAllowedOriginPattern("https://www.fitmatch.page");
        } else {
            origins.forEach(config::addAllowedOriginPattern);
        }

        List<String> methods = splitCsv(allowedMethods);
        if (methods.isEmpty()) {
            config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        } else {
            config.setAllowedMethods(methods);
        }

        List<String> headers = splitCsv(allowedHeaders);
        if (headers.isEmpty()) {
            config.addAllowedHeader("*");
        } else {
            config.setAllowedHeaders(headers);
        }

        List<String> exposed = splitCsv(exposedHeaders);
        if (!exposed.isEmpty()) {
            config.setExposedHeaders(exposed);
        }

        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }

    private static List<String> splitCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }
}
