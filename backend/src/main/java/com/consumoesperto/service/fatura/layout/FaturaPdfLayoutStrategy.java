package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Regras de leitura e pós-processamento por banco/emissor de fatura PDF.
 */
public interface FaturaPdfLayoutStrategy {

    BancoFaturaLayout layout();

    /** Maior = mais específico; vence em empate de {@link #reconhece(String)}. */
    default int prioridade() {
        return 50;
    }

    /** Verifica o texto bruto do PDF antes da importação. */
    boolean reconhece(String textoPdfNormalizado);

    /** Instruções adicionais no prompt principal da IA. */
    String instrucoesExtracaoIa();

    /** Instruções nos trechos de continuação (PDF longo). */
    default String instrucoesContinuacaoChunk() {
        return instrucoesExtracaoIa();
    }

    /** Sanitização defensiva dos lançamentos já parseados do JSON. */
    List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens);

    /**
     * Valor de referência para conciliação (pode diferir do total a pagar quando há saldo anterior etc.).
     */
    default BigDecimal resolverReferenciaConciliacao(
        JsonNode extracted,
        BigDecimal valorTotalPdf,
        List<ImportacaoFaturaItemDTO> itens,
        List<String> auditorias
    ) {
        return valorTotalPdf;
    }

    /** Ajusta bancoCartao inferido pela IA com base no PDF. */
    default String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return bancoExtraidoIa;
    }

    /** Complementa lançamentos omitidos pela IA usando o texto bruto do PDF. */
    default void complementarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        int anoReferencia
    ) {
        // padrão: sem complemento
    }

    /** Total da fatura lido deterministicamente do texto (fallback quando a IA não preenche). */
    default Optional<BigDecimal> extrairTotalFaturaDoTexto(String textoPdf) {
        return Optional.empty();
    }

    /** Segunda passagem de poda/conciliação com o total conhecido. */
    default void finalizarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        // padrão: sem finalização extra
    }
}
