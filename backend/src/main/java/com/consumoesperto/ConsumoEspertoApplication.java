package com.consumoesperto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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
@EnableFeignClients("com.consumoesperto.client") // Habilita os clientes Feign para integrações bancárias

public class ConsumoEspertoApplication {

    /**
     * Método principal que inicia a aplicação Spring Boot
     * 
     * @param args Argumentos de linha de comando passados para a aplicação
     */
    public static void main(String[] args) {
        // Inicia a aplicação Spring Boot
        // OAuth2 Client é desabilitado por padrão via @SpringBootApplication(exclude = ...)
        // Para habilitar, configure GOOGLE_CLIENT_ID e GOOGLE_CLIENT_SECRET via variáveis de ambiente
        // e remova OAuth2ClientAutoConfiguration.class do exclude na anotação @SpringBootApplication
        SpringApplication.run(ConsumoEspertoApplication.class, args);
    }
}
