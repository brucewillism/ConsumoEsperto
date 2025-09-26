package com.consumoesperto.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
@Slf4j
public class PublicTestController {

    @GetMapping("/health")
    @CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
    public Map<String, Object> health() {
        return Map.of(
            "status", "success",
            "message", "API pública funcionando",
            "timestamp", System.currentTimeMillis()
        );
    }

    @GetMapping("/info")
    @CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
    public Map<String, Object> info() {
        return Map.of(
            "name", "ConsumoEsperto API",
            "version", "1.0.0",
            "description", "API para gestão financeira pessoal",
            "security", "JWT Authentication Required"
        );
    }
}
