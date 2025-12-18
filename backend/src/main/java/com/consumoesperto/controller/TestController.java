package com.consumoesperto.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller responsável por endpoints de teste e verificação de saúde da API
 * 
 * Este controller expõe endpoints básicos para verificar o funcionamento
 * da aplicação, incluindo verificação de saúde (health check) e informações
 * básicas sobre a API. Útil para monitoramento e debugging.
 * 
 * Funcionalidades principais:
 * - Verificação de saúde da aplicação (health check)
 * - Informações básicas sobre a API
 * - Endpoints de teste para validação de funcionamento
 * - Monitoramento de status da aplicação
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/test") // Base path para endpoints de teste
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"}) // Permite CORS de qualquer origem
public class TestController {

    /**
     * Verifica a saúde e funcionamento da API
     * 
     * Endpoint para verificar se a aplicação está funcionando corretamente.
     * Retorna status, mensagem e timestamp para monitoramento e debugging.
     * 
     * @return Mapa contendo status da API, mensagem e timestamp
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        // Cria resposta com informações de saúde da API
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "ConsumoEsperto API está funcionando!");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * Endpoint raiz para informações básicas da API
     * 
     * Endpoint principal que retorna informações básicas sobre a API,
     * incluindo mensagem de boas-vindas e versão atual.
     * 
     * @return Mapa contendo mensagem de boas-vindas e versão da API
     */
    @GetMapping("/")
    public Map<String, String> root() {
        // Cria resposta com informações básicas da API
        Map<String, String> response = new HashMap<>();
        response.put("message", "Bem-vindo à API ConsumoEsperto!");
        response.put("version", "1.0.0");
        return response;
    }
}
