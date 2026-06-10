package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class SantanderFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.SANTANDER;
    }

    @Override
    public int prioridade() {
        return 86;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            && FaturaPdfLayoutSupport.contem(textoPdfNormalizado, "santander", "banco santander", "esfera");
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT SANTANDER: bancoCartao='Santander'. Extraia só despesas do período da fatura. "
            + "Ignore «Próximas faturas», simulações, limites, pontos Esfera e encargos projetados.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            if (!SantanderFaturaTextoExtrator.deveIgnorarDescricao(item.getDescricao())) {
                out.add(item);
            }
        }
        return out;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "Santander";
    }

    @Override
    public void complementarLancamentosDoTexto(String textoPdf, List<ImportacaoFaturaItemDTO> itens, int anoReferencia) {
        SantanderFaturaTextoExtrator.complementar(itens, textoPdf, anoReferencia);
    }

    @Override
    public Optional<BigDecimal> extrairTotalFaturaDoTexto(String textoPdf) {
        return SantanderFaturaTextoExtrator.extrairTotalFatura(textoPdf);
    }

    @Override
    public void finalizarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        SantanderFaturaTextoExtrator.finalizarLista(itens, textoPdf, totalFatura, anoReferencia);
    }
}
