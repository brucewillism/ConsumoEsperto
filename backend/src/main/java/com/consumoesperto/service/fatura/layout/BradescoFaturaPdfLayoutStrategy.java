package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class BradescoFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.BRADESCO;
    }

    @Override
    public int prioridade() {
        return 86;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            && FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "bradesco",
                "banco bradesco",
                "bradescard",
                "www bradesco com br"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT BRADESCO: bancoCartao='Bradesco'. Extraia só compras/parcelas/taxas do período da fatura. "
            + "Ignore «Próximas faturas», simulações de parcelamento, limites, pontos e encargos rotativos simulados. "
            + "Parcela N de M na linha seguinte = parcelaAtual/totalParcelas.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            if (BradescoFaturaTextoExtrator.deveIgnorarDescricao(item.getDescricao())) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "Bradesco";
    }

    @Override
    public void complementarLancamentosDoTexto(String textoPdf, List<ImportacaoFaturaItemDTO> itens, int anoReferencia) {
        BradescoFaturaTextoExtrator.complementar(itens, textoPdf, anoReferencia);
    }

    @Override
    public Optional<BigDecimal> extrairTotalFaturaDoTexto(String textoPdf) {
        return BradescoFaturaTextoExtrator.extrairTotalFatura(textoPdf);
    }

    @Override
    public void finalizarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        BradescoFaturaTextoExtrator.finalizarLista(itens, textoPdf, totalFatura, anoReferencia);
    }
}
