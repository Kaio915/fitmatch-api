package fitmatch_api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Inicializa o Firebase Admin SDK a partir das credenciais do Service Account.
 *
 * SEGURANÇA: as credenciais NUNCA ficam hardcoded no código. Elas são lidas
 * via variáveis de ambiente:
 *   - FIREBASE_CREDENTIALS_PATH: caminho do arquivo JSON (recomendado em VPS/Docker).
 *   - FIREBASE_CREDENTIALS_JSON: JSON inline (ou base64) — útil para Docker secrets.
 *
 * O bean só é criado quando "firebase.enabled=true" (FIREBASE_ENABLED=true),
 * para não quebrar o boot em ambientes sem FCM configurado.
 */
@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseMessaging firebaseMessaging(
            @Value("${firebase.credentials-path:}") String credentialsPath,
            @Value("${firebase.credentials-json:}") String credentialsJson
    ) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseMessaging.getInstance();
        }

        InputStream stream = resolveCredentialsStream(credentialsPath, credentialsJson);
        if (stream == null) {
            throw new IllegalStateException(
                    "firebase.enabled=true, mas nenhuma credencial foi fornecida. "
                            + "Defina FIREBASE_CREDENTIALS_PATH (caminho do arquivo JSON) "
                            + "ou FIREBASE_CREDENTIALS_JSON (JSON inline ou base64)."
            );
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build();

        FirebaseApp.initializeApp(options);
        log.info("Firebase Admin SDK inicializado com sucesso.");
        return FirebaseMessaging.getInstance();
    }

    private InputStream resolveCredentialsStream(String path, String json) throws IOException {
        // 1) Caminho do arquivo (recomendado: monte o JSON como secret no servidor).
        if (path != null && !path.isBlank()) {
            log.info("Inicializando Firebase a partir do arquivo: {}", path.trim());
            return new FileInputStream(path.trim());
        }

        // 2) JSON inline ou base64 (útil para ambientes sem sistema de arquivos).
        if (json != null && !json.isBlank()) {
            String value = json.trim();
            if (value.startsWith("{")) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }
            return new ByteArrayInputStream(Base64.getDecoder().decode(value));
        }

        return null;
    }
}
