package com.consumoesperto.service.ai;

import com.consumoesperto.dto.ai.structured.OcrComprovanteStructuredDTO;
import com.consumoesperto.dto.ai.structured.WhatsappDespesaStructuredDTO;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.validation.Validation;
import javax.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiStructuredOutputServiceTest {

    private AiStructuredOutputMetrics metrics;
    private AiStructuredOutputService service;

    @BeforeEach
    void setUp() {
        metrics = new AiStructuredOutputMetrics();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new AiStructuredOutputService(validator, metrics);
    }

    @Test
    void corrigeAliasValorParaAmount() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("action", "CREATE_EXPENSE");
        raw.put("valor", 42.50);
        raw.put("descricao", "Mercado");
        raw.put("confianca", 0.9);

        AiStructuredOutputResult<WhatsappDespesaStructuredDTO> result = service.parseAndValidate(
            raw,
            AiStructuredOutputKind.WHATSAPP_DESPESA,
            WhatsappDespesaStructuredDTO.class,
            1L,
            null
        );

        assertTrue(result.isValid());
        assertEquals(0, result.getPayload().getAmount().compareTo(new BigDecimal("42.50")));
        assertEquals(1L, metrics.snapshot().get("respostasValidas"));
    }

    @Test
    void rejeitaComprovanteSemValor() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("tipo", "DESPESA");
        raw.put("descricao", "PIX");
        raw.put("confianca", 0.8);

        AiStructuredOutputResult<OcrComprovanteStructuredDTO> result = service.parseAndValidate(
            raw,
            AiStructuredOutputKind.OCR_COMPROVANTE,
            OcrComprovanteStructuredDTO.class,
            2L,
            attempt -> raw
        );

        assertFalse(result.isValid());
        assertTrue(result.isRequiresUserConfirmation());
        assertEquals(1L, metrics.snapshot().get("respostasRejeitadas"));
    }

    @Test
    void normalizaTipoComprovante() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("valor", 10);
        raw.put("tipo", " receita ");
        raw.put("descricao", "Salário");
        raw.put("confianca", 0.95);

        AiStructuredOutputResult<OcrComprovanteStructuredDTO> result = service.parseAndValidate(
            raw,
            AiStructuredOutputKind.OCR_COMPROVANTE,
            OcrComprovanteStructuredDTO.class,
            3L,
            null
        );

        assertTrue(result.isValid());
        assertEquals("RECEITA", result.getPayload().getTipo());
    }

    @Test
    void validaTransferenciaEntreContas() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("action", "TRANSFER_BETWEEN_ACCOUNTS");
        raw.put("amount", 100);
        raw.put("contaOrigem", "Itaú");
        raw.put("contaDestino", "Nubank");
        raw.put("confianca", 0.92);

        var result = service.parseAndValidate(
            raw,
            AiStructuredOutputKind.TRANSFER_BETWEEN_ACCOUNTS,
            com.consumoesperto.dto.ai.structured.TransferBetweenAccountsStructuredDTO.class,
            4L,
            null
        );
        assertTrue(result.isValid());
    }

    @Test
    void rejeitaParcelamentoSemCartao() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("action", "CREATE_EXPENSE");
        raw.put("amount", 500);
        raw.put("description", "TV");
        raw.put("installmentCount", 3);
        raw.put("confianca", 0.9);

        var result = service.parseAndValidate(
            raw,
            AiStructuredOutputKind.INSTALLMENT_PURCHASE,
            com.consumoesperto.dto.ai.structured.InstallmentPurchaseStructuredDTO.class,
            5L,
            attempt -> raw
        );
        assertFalse(result.isValid());
        assertEquals(AiStructuredOutputStatus.NEEDS_CONFIRMATION, result.getStatus());
    }

    @Test
    void resolveKindParcelamento() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("action", "CREATE_EXPENSE");
        raw.put("installmentCount", 4);
        assertTrue(AiStructuredOutputKind.resolveWhatsappMutation(raw)
            .filter(k -> k == AiStructuredOutputKind.INSTALLMENT_PURCHASE)
            .isPresent());
    }
}
