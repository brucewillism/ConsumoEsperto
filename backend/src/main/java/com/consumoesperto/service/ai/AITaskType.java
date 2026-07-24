package com.consumoesperto.service.ai;

import com.consumoesperto.service.AiProviderType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Tipo de tarefa de IA — define a cadeia de fallback por categoria (não global).
 */
@Getter
@RequiredArgsConstructor
public enum AITaskType {

    CHAT(List.of(
        AiProviderType.CLAUDE,
        AiProviderType.OPENAI,
        AiProviderType.GEMINI
    )),

    FINANCIAL_ADVISOR(List.of(
        AiProviderType.CLAUDE,
        AiProviderType.OPENAI,
        AiProviderType.GEMINI
    )),

    STRUCTURED_OUTPUT(List.of(
        AiProviderType.OPENAI,
        AiProviderType.GROQ,
        AiProviderType.DEEPSEEK
    )),

    OCR_RECEIPT(List.of(
        AiProviderType.GEMINI,
        AiProviderType.OPENAI,
        AiProviderType.CLAUDE
    )),

    OCR_INVOICE(List.of(
        AiProviderType.GEMINI,
        AiProviderType.OPENAI,
        AiProviderType.CLAUDE
    )),

    OCR_PAYSLIP(List.of(
        AiProviderType.OPENAI,
        AiProviderType.GEMINI,
        AiProviderType.CLAUDE
    )),

    WHATSAPP_COMMAND(List.of(
        AiProviderType.GROQ,
        AiProviderType.DEEPSEEK,
        AiProviderType.OPENAI
    )),

    VOICE_TRANSCRIPTION(List.of(
        AiProviderType.GROQ,
        AiProviderType.OPENAI
    )),

    MEMORY_SUMMARY(List.of(
        AiProviderType.CLAUDE,
        AiProviderType.OPENAI
    )),

    REPORT_GENERATION(List.of(
        AiProviderType.CLAUDE,
        AiProviderType.OPENAI
    )),

    SEMANTIC_ANALYSIS(List.of(
        AiProviderType.CLAUDE,
        AiProviderType.OPENAI
    ));

    private final List<AiProviderType> providerChain;
}
