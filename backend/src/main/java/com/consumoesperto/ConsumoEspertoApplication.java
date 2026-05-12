package com.consumoesperto;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principal da aplicação ConsumoEsperto
 * 
 * Esta é a classe de inicialização do Spring Boot que configura e inicia
 * toda a aplicação. Ela define onde estão localizadas as entidades JPA
 * e os repositórios para o Spring Data.
 * 
 * OAuth2 Client é desabilitado por padrão para evitar erro de validação quando não configurado.
 * Para habilitar OAuth2, configure GOOGLE_CLIENT_ID e GOOGLE_CLIENT_SECRET via variáveis de ambiente
 * e remova OAuth2ClientAutoConfiguration.class do exclude abaixo.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@SpringBootApplication(exclude = OAuth2ClientAutoConfiguration.class)
@EntityScan("com.consumoesperto.model") // Define onde estão as entidades JPA
@EnableJpaRepositories("com.consumoesperto.repository") // Habilita os repositórios Spring Data
@EnableScheduling
@EnableAsync

public class ConsumoEspertoApplication {

    /**
     * Método principal que inicia a aplicação Spring Boot
     * 
     * @param args Argumentos de linha de comando passados para a aplicação
     */
    public static void main(String[] args) {
        applyDotenvFromRepoRoot();
        SpringApplication.run(ConsumoEspertoApplication.class, args);
    }

    /**
     * Carrega {@code .env} a partir da raiz do repositório (ou da pasta atual), antes do Spring.
     * Variáveis já definidas no SO/IDE têm prioridade. Sem isto, só Node/Evolution usavam o .env.
     */
    private static void applyDotenvFromRepoRoot() {
        Path dir = findDirectoryContainingEnv();
        if (dir == null) {
            return;
        }
        try {
            Dotenv dotenv = Dotenv.configure()
                .directory(dir.toString())
                .ignoreIfMissing()
                .load();
            int applied = 0;
            for (var e : dotenv.entries()) {
                String key = e.getKey();
                if (key == null || key.isBlank()) {
                    continue;
                }
                if (System.getenv(key) != null) {
                    continue;
                }
                String val = e.getValue();
                if (val == null || val.isBlank()) {
                    continue;
                }
                System.setProperty(key, val);
                applied++;
            }
            if (applied > 0) {
                System.out.println("[ConsumoEsperto] " + applied + " variável(is) do ficheiro .env aplicada(s) a partir de: "
                    + dir.resolve(".env"));
            }
        } catch (Exception ex) {
            System.err.println("[ConsumoEsperto] Aviso: .env não carregado — " + ex.getMessage());
        }
    }

    private static Path findDirectoryContainingEnv() {
        Path p = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int i = 0; i < 8; i++) {
            Path env = p.resolve(".env");
            if (Files.isRegularFile(env)) {
                return p;
            }
            if (p.getParent() == null) {
                break;
            }
            p = p.getParent();
        }
        return null;
    }
}
