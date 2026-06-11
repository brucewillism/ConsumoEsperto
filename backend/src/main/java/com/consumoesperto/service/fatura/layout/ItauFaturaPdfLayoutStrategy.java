package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ItauFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.ITAU;
    }

    @Override
    public int prioridade() {
        return 90;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        boolean sinaisItau = FaturaPdfLayoutSupport.contem(
            textoPdfNormalizado,
            "itau",
            "itaú unibanco",
            "itaucard",
            "www itau com br",
            "cartao itau"
        );
        if (!sinaisItau) {
            return false;
        }
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            || FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "demonstrativo",
                "total desta fatura",
                "valor total da fatura",
                "pagamento total",
                "vencimento",
                "pagamento minimo",
                "compras e saques",
                "lancamentos",
                "data estabelecimento"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT ITAÚ: padrão 'DD/MM estabelecimento … N/N valor' — o par N/N antes do valor "
            + "(ex.: '10/10 64,10') é parcela atual/total; o valor cobrado NESTA fatura é só o último número (64.10), "
            + "NUNCA 1064.10 por causa de '10/10'. Milhar só com ponto brasileiro explícito ('1.064,10'). "
            + "Inclua TODAS as seções que compõem o total: compras e saques, produtos e serviços (anuidade), "
            + "Encargos financeiros (IOF, juros rotativos, multa). bancoCartao='Itaú'. "
            + "Ignore limites, pontos e simulações. "
            + "REGRAS ESPECÍFICAS ITAÚ: parcelas no formato 'XX/XX' no texto do lançamento (ex.: '02/10', '3/12') "
            + "— preencha parcelaAtual e totalParcelas em cada lançamento; não confunda DD/MM (data) com parcela "
            + "(ex.: em '15/03 LOJA 03/12 89,90' a parcela é 03/12, não 15/03). "
            + "Também aceite valor antes da parcela na mesma linha (ex.: 'LOJA 89,90 03/12'). "
            + "'Melhor dia de compra' e data de fechamento NÃO indicam fatura aberta — use dataVencimento para o ciclo. "
            + "Quando parcelaAtual < totalParcelas, o backend gera faturas futuras; garanta parcelaAtual/totalParcelas "
            + "corretos em todos os lançamentos parcelados. "
            + "Não duplique a seção 'Compras parceladas - próximas faturas' nos lançamentos do mês atual.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            String n = FaturaPdfLayoutSupport.norm(item.getDescricao());
            if (n.contains("limite de credito") || n.contains("pontos itau") || n.contains("simulacao de parcelamento")) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        if (FaturaPdfLayoutSupport.bancoExtraidoUtil(bancoExtraidoIa)) {
            return bancoExtraidoIa;
        }
        return "Itaú";
    }

    @Override
    public void complementarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        int anoReferencia
    ) {
        ItauFaturaTextoExtrator.complementar(itens, textoPdf, anoReferencia);
    }

    @Override
    public Optional<BigDecimal> extrairTotalFaturaDoTexto(String textoPdf) {
        return ItauFaturaTextoExtrator.extrairTotalFatura(textoPdf);
    }

    @Override
    public void finalizarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        ItauFaturaTextoExtrator.complementar(itens, textoPdf, anoReferencia);
    }
}
