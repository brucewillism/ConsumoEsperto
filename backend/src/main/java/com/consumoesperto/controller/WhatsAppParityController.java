package com.consumoesperto.controller;

import com.consumoesperto.dto.WhatsAppParityItemDTO;
import com.consumoesperto.service.WhatsAppAppParityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class WhatsAppParityController {

    private final WhatsAppAppParityService parityService;

    /** Catálogo app ↔ WhatsApp (paridade de funcionalidades). */
    @GetMapping("/paridade")
    public ResponseEntity<Map<String, Object>> paridade(
        @RequestParam(required = false) String rota
    ) {
        List<WhatsAppParityItemDTO> itens = rota != null && !rota.isBlank()
            ? parityService.listarPorRota(rota)
            : parityService.listarTudo();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itens", itens);
        body.put("ajudaWhatsapp", "Envie *ajuda* ou *menu* no WhatsApp; *ajuda cartões* para um tema.");
        return ResponseEntity.ok(body);
    }
}
