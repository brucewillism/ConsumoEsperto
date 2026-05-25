package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.EvolutionUrlSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Garante instância Evolution dedicada por utilizador (evita partilhar um único QR/sessão WhatsApp).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvolutionInstanceLifecycleService {

    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final EvolutionPairingService evolutionPairingService;

    private RestTemplate restTemplate;

    @Value("${evolution.url:}")
    private String evolutionUrl;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    @Value("${evolution.instance:ConsumoEsperto}")
    private String defaultEvolutionInstance;

    /**
     * Quando true (recomendado em produção), cada utilizador recebe instância {@code ce-u{id}} na Evolution.
     */
    @Value("${consumoesperto.evolution.dedicated-instance-per-user:true}")
    private boolean dedicatedInstancePerUser;

    @PostConstruct
    void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(25_000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * Antes do pareamento: instância dedicada criada na Evolution e gravada em {@link UsuarioAiConfig}.
     *
     * @return nome da instância a usar em /instance/connect
     */
    @Transactional
    public String prepareInstanceForPairing(Long usuarioId) {
        String instanceName = resolveOrAssignInstanceName(usuarioId);
        if (!apiConfigured()) {
            log.warn("Evolution não configurada — instância {} não criada na API", instanceName);
            return instanceName;
        }
        createInstanceIfAbsent(instanceName);
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
        return instanceName;
    }

    /**
     * Ao desvincular: encerra sessão WhatsApp na instância (libera slot para novo QR).
     */
    @Transactional
    public void releaseInstanceOnUnlink(Long usuarioId) {
        String instanceName = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
        if (apiConfigured() && instanceName != null && !instanceName.isBlank()) {
            logoutInstanceQuietly(instanceName);
        }
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
    }

    /**
     * Reinicia sessão antes de novo QR (instância já ligada a outro telefone ou estado inconsistente).
     */
    public void resetSessionBeforePairing(Long usuarioId) {
        if (!apiConfigured()) {
            return;
        }
        String instanceName = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
        if (instanceName == null || instanceName.isBlank()) {
            return;
        }
        if (evolutionPairingService.isInstanceConnectedForUser(usuarioId)) {
            return;
        }
        logoutInstanceQuietly(instanceName);
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
    }

    @Transactional
    protected String resolveOrAssignInstanceName(Long usuarioId) {
        if (!dedicatedInstancePerUser || usuarioId == null) {
            return defaultInstanceTrimmed();
        }

        UsuarioAiConfig cfg = usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .orElseGet(() -> newConfigFor(usuarioId));

        String current = cfg.getEvolutionInstanceName();
        String sharedDefault = defaultInstanceTrimmed();
        boolean usesSharedOrEmpty = current == null
            || current.isBlank()
            || current.equalsIgnoreCase(sharedDefault);

        if (!usesSharedOrEmpty) {
            return current.trim();
        }

        String dedicated = dedicatedInstanceName(usuarioId);
        cfg.setEvolutionInstanceName(dedicated);
        usuarioAiConfigRepository.save(cfg);
        log.info("Instância Evolution dedicada atribuída ao utilizador {}: {}", usuarioId, dedicated);
        return dedicated;
    }

    private UsuarioAiConfig newConfigFor(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));
        UsuarioAiConfig c = new UsuarioAiConfig();
        c.setUsuario(u);
        c.setProviderOrderJson("[\"GROQ\",\"GEMINI\",\"OPENAI\",\"OLLAMA\"]");
        return c;
    }

    private static String dedicatedInstanceName(Long usuarioId) {
        return "ce-u" + usuarioId;
    }

    private String defaultInstanceTrimmed() {
        return defaultEvolutionInstance != null && !defaultEvolutionInstance.isBlank()
            ? defaultEvolutionInstance.trim()
            : "ConsumoEsperto";
    }

    private boolean apiConfigured() {
        return evolutionUrl != null
            && !evolutionUrl.isBlank()
            && evolutionApiKey != null
            && !evolutionApiKey.isBlank();
    }

    private void createInstanceIfAbsent(String instanceName) {
        String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/create");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instanceName", instanceName);
        body.put("integration", "WHATSAPP-BAILEYS");
        body.put("qrcode", false);

        try {
            evolutionPostJson(url, body);
            log.info("Evolution instância criada ou confirmada: {}", instanceName);
        } catch (HttpClientErrorException e) {
            if (e.getRawStatusCode() == 403 || e.getRawStatusCode() == 409) {
                log.debug("Evolution instância {} já existe (HTTP {})", instanceName, e.getRawStatusCode());
                return;
            }
            String altUrl = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/create/" + instanceName);
            try {
                evolutionPostJson(altUrl, Map.of());
                log.info("Evolution instância criada via path alternativo: {}", instanceName);
            } catch (HttpClientErrorException e2) {
                if (e2.getRawStatusCode() == 403 || e2.getRawStatusCode() == 409) {
                    log.debug("Evolution instância {} já existe (alt HTTP {})", instanceName, e2.getRawStatusCode());
                } else {
                    log.warn("Evolution create instance [{}]: HTTP {} — {}", instanceName, e2.getRawStatusCode(),
                        abbreviate(e2.getResponseBodyAsString(), 200));
                }
            }
        } catch (Exception ex) {
            log.warn("Evolution create instance [{}] falhou: {}", instanceName, ex.getMessage());
        }
    }

    private void logoutInstanceQuietly(String instanceName) {
        for (HttpMethod method : new HttpMethod[] { HttpMethod.DELETE, HttpMethod.POST }) {
            try {
                String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/logout/" + instanceName);
                evolutionRequest(url, method, null);
                log.info("Evolution logout ({}) para instância {}", method, instanceName);
                return;
            } catch (HttpClientErrorException e) {
                if (e.getRawStatusCode() == 404) {
                    continue;
                }
                log.debug("Evolution logout [{}] {}: HTTP {}", instanceName, method, e.getRawStatusCode());
            } catch (Exception ex) {
                log.debug("Evolution logout [{}] {}: {}", instanceName, method, ex.getMessage());
            }
        }
    }

    private void evolutionPostJson(String url, Map<String, Object> body) {
        evolutionRequest(url, HttpMethod.POST, body);
    }

    private void evolutionRequest(String url, HttpMethod method, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String key = evolutionApiKey.trim();
        headers.set("apikey", key);
        headers.setBearerAuth(key);
        HttpEntity<?> entity = body == null
            ? new HttpEntity<>(headers)
            : new HttpEntity<>(body, headers);
        restTemplate.exchange(url, method, entity, String.class);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
