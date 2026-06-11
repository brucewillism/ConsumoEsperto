package com.consumoesperto.service;

import com.consumoesperto.dto.NotificacaoParseadaDTO;
import com.consumoesperto.util.BancoBrasilCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationParserService {

    private static final String SYSTEM_PROMPT = """
        Você é o módulo de escuta em tempo real do J.A.R.V.I.S. (ConsumoEsperto).
        Analise notificações push de apps bancários no celular.
        Retorne APENAS JSON válido com os campos:
        - financeira (boolean): false se for marketing, promoção, segurança, código OTP, \
        lembrete não financeiro ou texto sem movimentação de dinheiro
        - banco (string): ITAU, NUBANK, INTER, MERCADO_PAGO ou OUTRO
        - tipo (string): CREDITO se o usuário recebeu dinheiro; DEBITO se gastou, pagou, enviou ou debitou
        - valor (number): valor em reais, sempre positivo; 0 se financeira=false
        - descricao (string): resumo curto, ex. "Pix recebido de Bruce Willis", "Compra Uber"
        """;

    private final OpenAiService openAiService;

    public NotificacaoParseadaDTO parse(Long usuarioId, String texto, String appPacote) {
        if (texto == null || texto.isBlank()) {
            return NotificacaoParseadaDTO.builder()
                .financeira(false)
                .banco("")
                .tipo(NotificacaoParseadaDTO.TipoMovimento.DEBITO)
                .valor(BigDecimal.ZERO)
                .descricao("")
                .build();
        }
        String bancoPacote = inferirBancoDoPacote(appPacote);
        String userPrompt = "Texto da notificação:\n" + texto.trim()
            + (bancoPacote.isBlank() ? "" : "\nPacote Android (dica): " + appPacote + " → banco provável: " + bancoPacote);

        JsonNode json = openAiService.gerarJson(usuarioId, SYSTEM_PROMPT, userPrompt);
        boolean financeira = json.path("financeira").asBoolean(true);
        if (!financeira) {
            log.info("[NOTIF-CELULAR] Notificação ignorada (não financeira): {}", resumir(texto));
            return NotificacaoParseadaDTO.builder()
                .financeira(false)
                .banco(normalizarBanco(json.path("banco").asText("")))
                .tipo(NotificacaoParseadaDTO.TipoMovimento.DEBITO)
                .valor(BigDecimal.ZERO)
                .descricao(json.path("descricao").asText("").trim())
                .build();
        }

        BigDecimal valor = parseValor(json.path("valor"));
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            financeira = false;
        }

        String tipoRaw = json.path("tipo").asText("DEBITO").trim().toUpperCase(Locale.ROOT);
        NotificacaoParseadaDTO.TipoMovimento tipo = "CREDITO".equals(tipoRaw)
            ? NotificacaoParseadaDTO.TipoMovimento.CREDITO
            : NotificacaoParseadaDTO.TipoMovimento.DEBITO;

        return NotificacaoParseadaDTO.builder()
            .financeira(financeira)
            .banco(normalizarBanco(json.path("banco").asText(inferirBancoDoTexto(texto, bancoPacote))))
            .tipo(tipo)
            .valor(valor)
            .descricao(json.path("descricao").asText("Movimentação bancária").trim())
            .build();
    }

    static String inferirBancoDoPacote(String appPacote) {
        if (appPacote == null || appPacote.isBlank()) {
            return "";
        }
        String p = appPacote.toLowerCase(Locale.ROOT);
        if (p.contains("nu.") || p.contains("nubank")) {
            return "nubank";
        }
        if (p.contains("itau") || p.contains("itaú")) {
            return "itau";
        }
        if (p.contains("inter")) {
            return "inter";
        }
        if (p.contains("mercadopago") || p.contains("mercadolibre") || p.contains("mp")) {
            return "mercadopago";
        }
        return "";
    }

    private static String inferirBancoDoTexto(String texto, String bancoPacote) {
        if (!bancoPacote.isBlank()) {
            return bancoPacote;
        }
        String norm = texto.toLowerCase(Locale.ROOT);
        if (norm.contains("nubank")) {
            return "nubank";
        }
        if (norm.contains("itaú") || norm.contains("itau")) {
            return "itau";
        }
        if (norm.contains("inter")) {
            return "inter";
        }
        if (norm.contains("mercado pago") || norm.contains("mercadopago")) {
            return "mercadopago";
        }
        return "OUTRO";
    }

    private static String normalizarBanco(String bancoIa) {
        if (bancoIa == null || bancoIa.isBlank()) {
            return "";
        }
        String ref = bancoIa.trim()
            .replace(' ', '_')
            .replace('-', '_')
            .toLowerCase(Locale.ROOT);
        if (ref.contains("mercado")) {
            return "mercadopago";
        }
        if (BancoBrasilCatalog.bancosCorrespondem(ref, "itau")) {
            return "itau";
        }
        if (BancoBrasilCatalog.bancosCorrespondem(ref, "nubank")) {
            return "nubank";
        }
        if (BancoBrasilCatalog.bancosCorrespondem(ref, "inter")) {
            return "inter";
        }
        if (BancoBrasilCatalog.bancosCorrespondem(ref, "mercadopago")) {
            return "mercadopago";
        }
        return ref;
    }

    private static BigDecimal parseValor(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            if (node.isNumber()) {
                return BigDecimal.valueOf(node.asDouble(0)).setScale(2, RoundingMode.HALF_UP);
            }
            String raw = node.asText("0").replace("R$", "").trim().replace(".", "").replace(",", ".");
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String resumir(String texto) {
        String t = texto.replace('\n', ' ').trim();
        return t.length() > 80 ? t.substring(0, 80) + "…" : t;
    }
}
