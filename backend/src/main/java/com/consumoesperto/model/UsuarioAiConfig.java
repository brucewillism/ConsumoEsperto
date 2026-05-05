package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

/**
 * Configuração de IA e WhatsApp por usuário (multitenant). Sem arquivo JSON global.
 */
@Entity
@Table(name = "usuario_ai_config")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioAiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    /** Nome da instância na Evolution API (webhook identifica o tenant). */
    @Column(name = "evolution_instance_name", unique = true, length = 128)
    private String evolutionInstanceName;

    /** Chave apikey da Evolution (opcional por utilizador; senão usa a global do servidor). */
    @Column(name = "evolution_api_key", columnDefinition = "TEXT")
    private String evolutionApiKey;

    @Column(name = "whatsapp_owner_phone", length = 32)
    private String whatsappOwnerPhone;

    @Column(name = "groq_api_key", columnDefinition = "TEXT")
    private String groqApiKey;

    @Column(name = "groq_base_url", length = 500)
    private String groqBaseUrl;

    @Column(name = "groq_model_text", length = 200)
    private String groqModelText;

    @Column(name = "groq_model_vision", length = 200)
    private String groqModelVision;

    @Column(name = "groq_whisper_model", length = 200)
    private String groqWhisperModel;

    @Column(name = "openai_api_key", columnDefinition = "TEXT")
    private String openaiApiKey;

    @Column(name = "openai_base_url", length = 500)
    private String openaiBaseUrl;

    @Column(name = "openai_model", length = 200)
    private String openaiModel;

    @Column(name = "openai_whisper_model", length = 200)
    private String openaiWhisperModel;

    @Column(name = "ollama_base_url", length = 500)
    private String ollamaBaseUrl;

    @Column(name = "ollama_model", length = 200)
    private String ollamaModel;

    /** JSON array, ex: ["GROQ","OPENAI","OLLAMA"] */
    @Column(name = "provider_order", length = 500)
    private String providerOrderJson;
}
