package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class BancoBrasilFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.BANCO_BRASIL;
    }

    @Override
    public int prioridade() {
        return 85;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            && FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "banco do brasil",
                "bb com br",
                "saldo fatura anterior",
                "lancamentos no cartao"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT BANCO DO BRASIL: preencha saldoFaturaAnterior e saldoFaturaAtual quando visíveis. "
            + "valorTotal = total a pagar. 'SALDO FATURA ANTERIOR' nos lançamentos NÃO é despesa nova. "
            + "bancoCartao='Banco do Brasil'. Ignore pagamentos recebidos e saldo restante da fatura anterior.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        return itens;
    }

    @Override
    public BigDecimal resolverReferenciaConciliacao(
        JsonNode extracted,
        BigDecimal valorTotalPdf,
        List<ImportacaoFaturaItemDTO> itens,
        List<String> auditorias
    ) {
        return FaturaPdfLayoutConciliacao.preferirSaldoFaturaAtual(extracted, valorTotalPdf, itens, auditorias);
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "Banco do Brasil";
    }
}
