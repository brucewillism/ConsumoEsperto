package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class XpFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.XP;
    }

    @Override
    public int prioridade() {
        return 83;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            && FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "xp investimentos",
                "xp inc",
                "cartao xp",
                "visa infinite xp"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT XP INVESTIMENTOS: bancoCartao='XP'. Extraia compras/parcelas do período da fatura. "
            + "Ignore simulações, limites e «Próximas faturas».";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            if (!XpFaturaTextoExtrator.deveIgnorarDescricao(item.getDescricao())) {
                out.add(item);
            }
        }
        return out;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "XP";
    }

    @Override
    public void complementarLancamentosDoTexto(String textoPdf, List<ImportacaoFaturaItemDTO> itens, int anoReferencia) {
        XpFaturaTextoExtrator.complementar(itens, textoPdf, anoReferencia);
    }

    @Override
    public Optional<BigDecimal> extrairTotalFaturaDoTexto(String textoPdf) {
        return XpFaturaTextoExtrator.extrairTotalFatura(textoPdf);
    }

    @Override
    public void finalizarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        XpFaturaTextoExtrator.finalizarLista(itens, textoPdf, totalFatura, anoReferencia);
    }
}
