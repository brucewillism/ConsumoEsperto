package com.consumoesperto.model;

/**
 * Origem do conteúdo que chega ao pipeline de captura automática de memória.
 *
 * <p>Guardrail anti-injection estrutural (não por convenção): apenas texto digitado ou áudio
 * transcrito do PRÓPRIO usuário pode gerar memória automática. Conteúdo de documento (PDF/OCR)
 * ou de URL externa é recusado dentro do {@code MemoriaCapturaAutomaticaService}.</p>
 */
public enum OrigemConteudo {

    TEXTO_USUARIO,
    AUDIO_TRANSCRITO,
    DOCUMENTO,
    URL_NFCE;

    /** Só conteúdo produzido diretamente pelo usuário pode virar memória automática. */
    public boolean podeGerarMemoriaAutomatica() {
        return this == TEXTO_USUARIO || this == AUDIO_TRANSCRITO;
    }
}
