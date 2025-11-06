package com.consumoesperto.controller;

import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.repository.BankApiConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller para iniciar o fluxo OAuth2 do Mercado Pago
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/oauth2")
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "OAuth2 Flow", description = "Endpoints para iniciar fluxo OAuth2")
public class OAuth2FlowController {

    private final BankApiConfigRepository bankApiConfigRepository;

    /**
     * Inicia o fluxo OAuth2 do Mercado Pago
     * 
     * @return URL de autorização do Mercado Pago
     */
    @GetMapping("/mercadopago/init")
    @Operation(summary = "Iniciar OAuth2 Mercado Pago", description = "Gera URL para autorização do Mercado Pago")
    public ResponseEntity<Map<String, Object>> startMercadoPagoOAuth2() {
        try {
            log.info("🚀 Iniciando fluxo OAuth2 do Mercado Pago");

            // Buscar configuração do Mercado Pago
            Optional<BankApiConfig> configOpt = bankApiConfigRepository.findByUsuarioIdAndTipoBanco(1L, "MERCADO_PAGO");
            
            if (configOpt.isEmpty()) {
                log.error("❌ Configuração do Mercado Pago não encontrada");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Configuração do Mercado Pago não encontrada"));
            }

            BankApiConfig config = configOpt.get();
            
            // Gerar URL de autorização
            String authUrl = buildAuthorizationUrl(config);
            
            log.info("✅ URL de autorização gerada: {}", authUrl);

            Map<String, Object> response = new HashMap<>();
            response.put("authorization_url", authUrl);
            response.put("message", "Acesse a URL para autorizar o acesso ao Mercado Pago");
            response.put("instructions", "Após autorizar, você será redirecionado de volta para a aplicação");
            response.put("callback_url", "https://0d723f1e294f.ngrok-free.app/api/bank/oauth/mercadopago/callback");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erro ao gerar URL de autorização: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Erro ao gerar URL de autorização"));
        }
    }

    /**
     * Constrói a URL de autorização do Mercado Pago
     * 
     * @param config Configuração do Mercado Pago
     * @return URL de autorização
     */
    private String buildAuthorizationUrl(BankApiConfig config) {
        StringBuilder url = new StringBuilder("https://auth.mercadopago.com/authorization");
        url.append("?client_id=").append(URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8));
        url.append("&response_type=code");
        url.append("&platform_id=mp");
        url.append("&redirect_uri=").append(URLEncoder.encode(config.getRedirectUri(), StandardCharsets.UTF_8));
        url.append("&state=").append(URLEncoder.encode("mercadopago_oauth", StandardCharsets.UTF_8));
        
        return url.toString();
    }
}
