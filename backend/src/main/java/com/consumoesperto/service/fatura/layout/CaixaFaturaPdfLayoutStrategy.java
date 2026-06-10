package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class CaixaFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.CAIXA;
    }

    @Override
    public int prioridade() {
        return 84;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            && FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "caixa economica",
                "caixa economica federal",
                "cef ",
                "cartao caixa"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT CAIXA: bancoCartao='Caixa'. Extraia lançamentos do cartão do período. "
            + "Ignore limites, simulações de parcelamento e «Próximas faturas».";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            if (!CaixaFaturaTextoExtrator.deveIgnorarDescricao(item.getDescricao())) {
                out.add(item);
            }
        }
        return out;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "Caixa";
    }

    @Override
    public void complementarLancamentosDoTexto(String textoPdf, List<ImportacaoFaturaItemDTO> itens, int anoReferencia) {
        CaixaFaturaTextoExtrator.complementar(itens, textoPdf, anoReferencia);
    }

    @Override
    public Optional<BigDecimal> extrairTotalFaturaDoTexto(String textoPdf) {
        return CaixaFaturaTextoExtrator.extrairTotalFatura(textoPdf);
    }

    @Override
    public void finalizarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        CaixaFaturaTextoExtrator.finalizarLista(itens, textoPdf, totalFatura, anoReferencia);
    }
}
