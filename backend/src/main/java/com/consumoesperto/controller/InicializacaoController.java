package com.consumoesperto.controller;

import com.consumoesperto.service.AutoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para inicialização manual do sistema
 */
@RestController
@RequestMapping("/api/inicializacao")
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
public class InicializacaoController {

    private static final Logger logger = LoggerFactory.getLogger(InicializacaoController.class);

    @Autowired
    private AutoSyncService autoSyncService;

    /**
     * Inicializa as categorias padrão do sistema
     */
    @PostMapping("/categorias")
    public ResponseEntity<Map<String, Object>> inicializarCategorias() {
        logger.info("🔧 Inicializando categorias padrão manualmente...");
        
        try {
            autoSyncService.criarCategoriasPadrao();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Categorias padrão inicializadas com sucesso!");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Erro ao inicializar categorias: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro ao inicializar categorias: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Verifica o status do sistema
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> statusSistema() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "Sistema funcionando em modo manual");
        response.put("sincronizacao_automatica", false);
        response.put("message", "O sistema não sincroniza automaticamente com APIs externas. Use os endpoints manuais para cadastrar dados.");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
