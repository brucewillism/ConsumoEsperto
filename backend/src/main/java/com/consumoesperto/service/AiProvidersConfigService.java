package com.consumoesperto.service;

import com.consumoesperto.security.SecurityService;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.ApiKeyMasking;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Configuração de provedores de IA por usuário (PostgreSQL). Sem {@code ai-providers.json}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiProvidersConfigService {

    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;
    private final SecurityService securityService;

    /** Chave Groq mestra (servidor). Usada só quando o utilizador não tem chave na BD. */
    @Value("${consumoesperto.ai.platform-groq-api-key:}")
    private String platformGroqApiKey;

    /**
     * Preenche a chave Groq em memória: prioridade à chave do utilizador; se vazia, usa a mestra do servidor.
     */
    public void applyGroqMasterFallback(AiProvidersConfig cfg) {
        if (cfg == null) {
            return;
        }
        GroqSection g = cfg.getGroq();
        if (g == null) {
            g = new GroqSection();
            GroqSection def = defaultGroq();
            g.setBaseUrl(def.getBaseUrl());
            g.setModelText(def.getModelText());
            g.setModelVision(def.getModelVision());
            g.setWhisperModel(def.getWhisperModel());
            cfg.setGroq(g);
        }
        if (meaningful(g.getApiKey())) {
            return;
        }
        if (platformGroqApiKey == null || platformGroqApiKey.isBlank()) {
            return;
        }
        g.setApiKey(platformGroqApiKey.trim());
    }

    @Transactional(readOnly = true)
    public Optional<String> findEvolutionApiKeyForUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        return usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .map(UsuarioAiConfig::getEvolutionApiKey)
            .filter(AiProvidersConfigService::meaningful);
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolveUsuarioIdByEvolutionInstance(String instanceName) {
        if (instanceName == null || instanceName.isBlank()) {
            return Optional.empty();
        }
        return usuarioAiConfigRepository.findByEvolutionInstanceNameIgnoreCase(instanceName.trim())
            .map(c -> c.getUsuario().getId());
    }

    public AiProvidersConfig load(Long usuarioId) {
        if (usuarioId == null) {
            return defaults();
        }
        return usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .map(this::toDto)
            .orElseGet(AiProvidersConfigService::defaults);
    }

    public AiProvidersConfig loadForCurrentUser() {
        return securityService.getCurrentUser()
            .map(u -> load(u.getId()))
            .orElseGet(AiProvidersConfigService::defaults);
    }

    @Transactional
    public AiProvidersConfig persistConfigFromApiRequest(Long usuarioId, AiProvidersConfig payload) {
        return mergeAndSave(usuarioId, payload);
    }

    @Transactional(readOnly = true)
    public SanitizedAiProvidersConfig getConfigForApiResponse(Long usuarioId) {
        return loadSanitized(usuarioId);
    }

    @Transactional
    public AiProvidersConfig mergeAndSave(Long usuarioId, AiProvidersConfig incoming) {
        if (incoming == null) {
            return load(usuarioId);
        }
        UsuarioAiConfig entity = usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .orElseGet(() -> newEntity(usuarioId));

        if (incoming.getProviderOrder() != null && !incoming.getProviderOrder().isEmpty()) {
            writeProviderOrder(entity, incoming.getProviderOrder());
        }
        if (incoming.getEvolutionInstanceName() != null) {
            String v = incoming.getEvolutionInstanceName().trim();
            entity.setEvolutionInstanceName(v.isEmpty() ? null : v);
        }
        if (incoming.getWhatsappOwnerPhone() != null) {
            String v = incoming.getWhatsappOwnerPhone().trim();
            entity.setWhatsappOwnerPhone(v.isEmpty() ? null : v);
        }
        mergeGroqEntity(entity, incoming.getGroq());
        mergeOpenaiEntity(entity, incoming.getOpenai());
        mergeOllamaEntity(entity, incoming.getOllama());
        usuarioAiConfigRepository.save(entity);
        return toDto(entity);
    }

    private UsuarioAiConfig newEntity(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));
        UsuarioAiConfig c = new UsuarioAiConfig();
        c.setUsuario(u);
        applyDefaultsToNewEntity(c);
        return c;
    }

    private void applyDefaultsToNewEntity(UsuarioAiConfig c) {
        GroqSection g = defaultGroq();
        OpenaiSection o = defaultOpenai();
        OllamaSection l = defaultOllama();
        c.setGroqBaseUrl(g.getBaseUrl());
        c.setGroqModelText(g.getModelText());
        c.setGroqModelVision(g.getModelVision());
        c.setGroqWhisperModel(g.getWhisperModel());
        c.setOpenaiBaseUrl(o.getBaseUrl());
        c.setOpenaiModel(o.getModel());
        c.setOpenaiWhisperModel(o.getWhisperModel());
        c.setOllamaBaseUrl(l.getBaseUrl());
        c.setOllamaModel(l.getModel());
        try {
            c.setProviderOrderJson(objectMapper.writeValueAsString(List.of("GROQ", "OPENAI", "OLLAMA")));
        } catch (Exception e) {
            c.setProviderOrderJson("[\"GROQ\",\"OPENAI\",\"OLLAMA\"]");
        }
    }

    private void mergeGroqEntity(UsuarioAiConfig entity, GroqSection inc) {
        if (inc == null) {
            return;
        }
        if (meaningful(inc.getApiKey())) {
            entity.setGroqApiKey(inc.getApiKey().trim());
        }
        if (meaningful(inc.getBaseUrl())) {
            entity.setGroqBaseUrl(inc.getBaseUrl().trim());
        }
        if (meaningful(inc.getModelText())) {
            entity.setGroqModelText(inc.getModelText().trim());
        }
        if (meaningful(inc.getModelVision())) {
            entity.setGroqModelVision(inc.getModelVision().trim());
        }
        if (meaningful(inc.getWhisperModel())) {
            entity.setGroqWhisperModel(inc.getWhisperModel().trim());
        }
    }

    private void mergeOpenaiEntity(UsuarioAiConfig entity, OpenaiSection inc) {
        if (inc == null) {
            return;
        }
        if (meaningful(inc.getApiKey())) {
            entity.setOpenaiApiKey(inc.getApiKey().trim());
        }
        if (meaningful(inc.getBaseUrl())) {
            entity.setOpenaiBaseUrl(inc.getBaseUrl().trim());
        }
        if (meaningful(inc.getModel())) {
            entity.setOpenaiModel(inc.getModel().trim());
        }
        if (meaningful(inc.getWhisperModel())) {
            entity.setOpenaiWhisperModel(inc.getWhisperModel().trim());
        }
    }

    private void mergeOllamaEntity(UsuarioAiConfig entity, OllamaSection inc) {
        if (inc == null) {
            return;
        }
        if (meaningful(inc.getBaseUrl())) {
            entity.setOllamaBaseUrl(inc.getBaseUrl().trim());
        }
        if (meaningful(inc.getModel())) {
            entity.setOllamaModel(inc.getModel().trim());
        }
    }

    private AiProvidersConfig toDto(UsuarioAiConfig e) {
        AiProvidersConfig dto = new AiProvidersConfig();
        dto.setEvolutionInstanceName(e.getEvolutionInstanceName());
        dto.setWhatsappOwnerPhone(e.getWhatsappOwnerPhone());
        dto.setProviderOrder(readProviderOrder(e.getProviderOrderJson()));

        GroqSection g = new GroqSection();
        g.setApiKey(e.getGroqApiKey());
        g.setBaseUrl(nvl(e.getGroqBaseUrl(), defaultGroq().getBaseUrl()));
        g.setModelText(nvl(e.getGroqModelText(), defaultGroq().getModelText()));
        g.setModelVision(nvl(e.getGroqModelVision(), defaultGroq().getModelVision()));
        g.setWhisperModel(nvl(e.getGroqWhisperModel(), defaultGroq().getWhisperModel()));
        dto.setGroq(g);

        OpenaiSection o = new OpenaiSection();
        o.setApiKey(e.getOpenaiApiKey());
        o.setBaseUrl(nvl(e.getOpenaiBaseUrl(), defaultOpenai().getBaseUrl()));
        o.setModel(nvl(e.getOpenaiModel(), defaultOpenai().getModel()));
        o.setWhisperModel(nvl(e.getOpenaiWhisperModel(), defaultOpenai().getWhisperModel()));
        dto.setOpenai(o);

        OllamaSection l = new OllamaSection();
        l.setBaseUrl(nvl(e.getOllamaBaseUrl(), defaultOllama().getBaseUrl()));
        l.setModel(nvl(e.getOllamaModel(), defaultOllama().getModel()));
        dto.setOllama(l);

        return normalize(dto);
    }

    private List<String> readProviderOrder(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>(List.of("GROQ", "OPENAI", "OLLAMA"));
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() { });
            return list != null && !list.isEmpty() ? new ArrayList<>(list) : new ArrayList<>(List.of("GROQ", "OPENAI", "OLLAMA"));
        } catch (Exception e) {
            return new ArrayList<>(List.of("GROQ", "OPENAI", "OLLAMA"));
        }
    }

    private void writeProviderOrder(UsuarioAiConfig entity, List<String> order) {
        try {
            entity.setProviderOrderJson(objectMapper.writeValueAsString(order));
        } catch (Exception e) {
            log.warn("Falha ao serializar providerOrder: {}", e.getMessage());
        }
    }

    private SanitizedAiProvidersConfig loadSanitized(Long usuarioId) {
        AiProvidersConfig raw = load(usuarioId);
        GroqSection g = raw.getGroq() != null ? raw.getGroq() : defaultGroq();
        OpenaiSection o = raw.getOpenai() != null ? raw.getOpenai() : defaultOpenai();
        OllamaSection l = raw.getOllama() != null ? raw.getOllama() : defaultOllama();
        return new SanitizedAiProvidersConfig(
            raw.getProviderOrder() != null ? new ArrayList<>(raw.getProviderOrder()) : List.of(),
            g.getApiKey() != null && !g.getApiKey().isBlank(),
            o.getApiKey() != null && !o.getApiKey().isBlank(),
            l.getBaseUrl() != null && !l.getBaseUrl().isBlank(),
            nz(g.getBaseUrl()),
            nz(g.getModelText()),
            nz(g.getModelVision()),
            nz(g.getWhisperModel()),
            nz(o.getBaseUrl()),
            nz(o.getModel()),
            nz(o.getWhisperModel()),
            nz(l.getBaseUrl()),
            nz(l.getModel()),
            nz(raw.getWhatsappOwnerPhone()),
            nz(raw.getEvolutionInstanceName()),
            ApiKeyMasking.maskApiKey(g.getApiKey()),
            ApiKeyMasking.maskApiKey(o.getApiKey())
        );
    }

    private static AiProvidersConfig normalize(AiProvidersConfig c) {
        if (c.getProviderOrder() == null || c.getProviderOrder().isEmpty()) {
            c.setProviderOrder(new ArrayList<>(List.of("GROQ", "OPENAI", "OLLAMA")));
        }
        if (c.getGroq() == null) {
            c.setGroq(defaultGroq());
        }
        if (c.getOpenai() == null) {
            c.setOpenai(defaultOpenai());
        }
        if (c.getOllama() == null) {
            c.setOllama(defaultOllama());
        }
        return c;
    }

    private static AiProvidersConfig defaults() {
        AiProvidersConfig c = new AiProvidersConfig();
        c.setProviderOrder(new ArrayList<>(List.of("GROQ", "OPENAI", "OLLAMA")));
        c.setGroq(defaultGroq());
        c.setOpenai(defaultOpenai());
        c.setOllama(defaultOllama());
        return c;
    }

    private static GroqSection defaultGroq() {
        GroqSection g = new GroqSection();
        g.setBaseUrl("https://api.groq.com/openai/v1");
        g.setModelText("llama-3.3-70b-versatile");
        g.setModelVision("llama-3.2-11b-vision-preview");
        g.setWhisperModel("whisper-large-v3");
        return g;
    }

    private static OpenaiSection defaultOpenai() {
        OpenaiSection o = new OpenaiSection();
        o.setBaseUrl("https://api.openai.com/v1");
        o.setModel("gpt-4o-mini");
        o.setWhisperModel("gpt-4o-mini-transcribe");
        return o;
    }

    /**
     * Sem URL por defeito: evita tentar {@code localhost:11434} quando o Ollama não está instalado
     * (voz/OCR iam falhar com connection refused). Quem usa Ollama preenche a base URL na config.
     */
    private static OllamaSection defaultOllama() {
        OllamaSection l = new OllamaSection();
        l.setBaseUrl(null);
        l.setModel("llama3.2");
        return l;
    }

    private static boolean meaningful(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String nvl(String a, String b) {
        return meaningful(a) ? a : b;
    }

    public static class AiProvidersConfig {
        private List<String> providerOrder = new ArrayList<>(List.of("GROQ", "OPENAI", "OLLAMA"));
        /** Nome da instância Evolution vinculada a este usuário (webhook). */
        private String evolutionInstanceName;
        private String whatsappOwnerPhone;
        private GroqSection groq;
        private OpenaiSection openai;
        private OllamaSection ollama;

        public List<String> getProviderOrder() {
            return providerOrder;
        }

        public void setProviderOrder(List<String> providerOrder) {
            this.providerOrder = providerOrder;
        }

        public String getEvolutionInstanceName() {
            return evolutionInstanceName;
        }

        public void setEvolutionInstanceName(String evolutionInstanceName) {
            this.evolutionInstanceName = evolutionInstanceName;
        }

        public String getWhatsappOwnerPhone() {
            return whatsappOwnerPhone;
        }

        public void setWhatsappOwnerPhone(String whatsappOwnerPhone) {
            this.whatsappOwnerPhone = whatsappOwnerPhone;
        }

        public GroqSection getGroq() {
            return groq;
        }

        public void setGroq(GroqSection groq) {
            this.groq = groq;
        }

        public OpenaiSection getOpenai() {
            return openai;
        }

        public void setOpenai(OpenaiSection openai) {
            this.openai = openai;
        }

        public OllamaSection getOllama() {
            return ollama;
        }

        public void setOllama(OllamaSection ollama) {
            this.ollama = ollama;
        }
    }

    public static class GroqSection {
        private String apiKey;
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String modelText = "llama-3.3-70b-versatile";
        private String modelVision = "llama-3.2-11b-vision-preview";
        private String whisperModel = "whisper-large-v3";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModelText() {
            return modelText;
        }

        public void setModelText(String modelText) {
            this.modelText = modelText;
        }

        public String getModelVision() {
            return modelVision;
        }

        public void setModelVision(String modelVision) {
            this.modelVision = modelVision;
        }

        public String getWhisperModel() {
            return whisperModel;
        }

        public void setWhisperModel(String whisperModel) {
            this.whisperModel = whisperModel;
        }
    }

    public static class OpenaiSection {
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o-mini";
        private String whisperModel = "gpt-4o-mini-transcribe";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getWhisperModel() {
            return whisperModel;
        }

        public void setWhisperModel(String whisperModel) {
            this.whisperModel = whisperModel;
        }
    }

    public static class OllamaSection {
        private String baseUrl = "http://localhost:11434/v1";
        private String model = "llama3.2";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class SanitizedAiProvidersConfig {
        private final List<String> providerOrder;
        private final boolean groqConfigured;
        private final boolean openaiConfigured;
        private final boolean ollamaConfigured;
        private final String groqBaseUrl;
        private final String groqModelText;
        private final String groqModelVision;
        private final String groqWhisperModel;
        private final String openaiBaseUrl;
        private final String openaiModel;
        private final String openaiWhisperModel;
        private final String ollamaBaseUrl;
        private final String ollamaModel;
        private final String whatsappOwnerPhone;
        private final String evolutionInstanceName;
        private final String groqApiKeyMasked;
        private final String openaiApiKeyMasked;

        public SanitizedAiProvidersConfig(
            List<String> providerOrder,
            boolean groqConfigured,
            boolean openaiConfigured,
            boolean ollamaConfigured,
            String groqBaseUrl,
            String groqModelText,
            String groqModelVision,
            String groqWhisperModel,
            String openaiBaseUrl,
            String openaiModel,
            String openaiWhisperModel,
            String ollamaBaseUrl,
            String ollamaModel,
            String whatsappOwnerPhone,
            String evolutionInstanceName,
            String groqApiKeyMasked,
            String openaiApiKeyMasked
        ) {
            this.providerOrder = providerOrder;
            this.groqConfigured = groqConfigured;
            this.openaiConfigured = openaiConfigured;
            this.ollamaConfigured = ollamaConfigured;
            this.groqBaseUrl = groqBaseUrl;
            this.groqModelText = groqModelText;
            this.groqModelVision = groqModelVision;
            this.groqWhisperModel = groqWhisperModel;
            this.openaiBaseUrl = openaiBaseUrl;
            this.openaiModel = openaiModel;
            this.openaiWhisperModel = openaiWhisperModel;
            this.ollamaBaseUrl = ollamaBaseUrl;
            this.ollamaModel = ollamaModel;
            this.whatsappOwnerPhone = whatsappOwnerPhone;
            this.evolutionInstanceName = evolutionInstanceName;
            this.groqApiKeyMasked = groqApiKeyMasked;
            this.openaiApiKeyMasked = openaiApiKeyMasked;
        }

        public List<String> getProviderOrder() {
            return providerOrder;
        }

        public boolean isGroqConfigured() {
            return groqConfigured;
        }

        public boolean isOpenaiConfigured() {
            return openaiConfigured;
        }

        public boolean isOllamaConfigured() {
            return ollamaConfigured;
        }

        public String getGroqBaseUrl() {
            return groqBaseUrl;
        }

        public String getGroqModelText() {
            return groqModelText;
        }

        public String getGroqModelVision() {
            return groqModelVision;
        }

        public String getGroqWhisperModel() {
            return groqWhisperModel;
        }

        public String getOpenaiBaseUrl() {
            return openaiBaseUrl;
        }

        public String getOpenaiModel() {
            return openaiModel;
        }

        public String getOpenaiWhisperModel() {
            return openaiWhisperModel;
        }

        public String getOllamaBaseUrl() {
            return ollamaBaseUrl;
        }

        public String getOllamaModel() {
            return ollamaModel;
        }

        public String getWhatsappOwnerPhone() {
            return whatsappOwnerPhone;
        }

        public String getEvolutionInstanceName() {
            return evolutionInstanceName;
        }

        public String getGroqApiKeyMasked() {
            return groqApiKeyMasked;
        }

        public String getOpenaiApiKeyMasked() {
            return openaiApiKeyMasked;
        }
    }
}
