package com.consumoesperto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.PropertySource;

/**
 * Classe principal da aplicação ConsumoEsperto
 * 
 * Esta é a classe de inicialização do Spring Boot que configura e inicia
 * toda a aplicação. Ela define onde estão localizadas as entidades JPA
 * e os repositórios para o Spring Data.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@SpringBootApplication
@EntityScan("com.consumoesperto.model") // Define onde estão as entidades JPA
@EnableJpaRepositories("com.consumoesperto.repository") // Habilita os repositórios Spring Data
@PropertySource({"classpath:mercadopago-config.properties"}) // Carrega configurações do Mercado Pago
public class ConsumoEspertoApplication {

    /**
     * Método principal que inicia a aplicação Spring Boot
     * 
     * @param args Argumentos de linha de comando passados para a aplicação
     */
    public static void main(String[] args) {
        // Inicia a aplicação Spring Boot com a classe atual
        SpringApplication.run(ConsumoEspertoApplication.class, args);
    }
}
