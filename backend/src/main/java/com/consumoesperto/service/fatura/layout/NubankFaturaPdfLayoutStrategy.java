package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NubankFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.NUBANK;
    }

    @Override
    public int prioridade() {
        return 100;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            && FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "nu pagamentos",
                "nubank",
                "ola esta e a sua fatura",
                "total de compras de todos os cartoes"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT NUBANK: a seção 'TRANSAÇÕES' começa com CABEÇALHO/SUBTOTAL do titular "
            + "(ex.: 'Bruce W M Silva R$ 2.425,51') e blocos como 'Pagamentos e Financiamentos' — NÃO são lançamentos. "
            + "Todo lançamento real começa com DATA (ex.: '25 ABR') e final do cartão ('•••• 3443'). "
            + "Pix/boleto no crédito: UMA linha por operação com o 'Total a pagar' — NUNCA separe valor da transação, IOF ou juros. "
            + "Parcelas 'Parcela 6/10 R$ 52,00' = valor DESTE mês (52.00). "
            + "bancoCartao='Nubank'. Referência: 'Total de compras de todos os cartões'. "
            + "Ignore SAC, CET, simulações de parcelamento, PRÓXIMAS FATURAS e LIMITES.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            return itens;
        }
        return removerSubtotaisPortador(removerComponentesPixDuplicados(itens));
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
        return "Nubank";
    }

    static List<ImportacaoFaturaItemDTO> removerComponentesPixDuplicados(List<ImportacaoFaturaItemDTO> itens) {
        Set<LocalDate> datasComTotalPagar = itens.stream()
            .filter(i -> i.getData() != null && contemTotalAPagar(i.getDescricao()))
            .map(ImportacaoFaturaItemDTO::getData)
            .collect(Collectors.toSet());
        if (datasComTotalPagar.isEmpty()) {
            return itens;
        }
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            if (item.getData() != null
                && datasComTotalPagar.contains(item.getData())
                && pareceComponenteDeTotalAPagar(item.getDescricao())) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    static List<ImportacaoFaturaItemDTO> removerSubtotaisPortador(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            if (!pareceSubtotalPortador(item.getDescricao())) {
                out.add(item);
            }
        }
        return out;
    }

    private static boolean contemTotalAPagar(String descricao) {
        return FaturaPdfLayoutSupport.norm(descricao).contains("total a pagar");
    }

    private static boolean pareceComponenteDeTotalAPagar(String descricao) {
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.isBlank() || n.contains("total a pagar")) {
            return false;
        }
        return n.contains("valor da transacao")
            || n.contains("valor da transa")
            || n.matches(".*\\bde\\s+iof\\b.*")
            || n.matches(".*\\biof\\s+de\\b.*")
            || (n.contains("iof") && n.length() < 42)
            || (n.contains("juros") && n.length() < 48 && !n.contains("rotativo") && !n.contains("encargos"));
    }

    private static boolean pareceSubtotalPortador(String descricao) {
        if (descricao == null || descricao.isBlank() || descricao.contains("••••")) {
            return false;
        }
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.isBlank() || n.contains("parcela") || n.contains("total a pagar")) {
            return false;
        }
        if (n.matches(".*\\d.*")) {
            return false;
        }
        String[] tokens = n.split(" ");
        return tokens.length >= 2 && tokens.length <= 7 && n.length() <= 52;
    }
}
