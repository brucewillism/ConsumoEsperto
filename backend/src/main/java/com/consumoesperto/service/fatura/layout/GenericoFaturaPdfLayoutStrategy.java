package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GenericoFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.GENERICO;
    }

    @Override
    public int prioridade() {
        return 0;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        return false;
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT GENÉRICO: extraia todos os lançamentos do período com data e valor cobrado nesta fatura. "
            + "Ignore subtotais, simulações de parcelamento, limites e rodapé institucional.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        return itens;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        if (FaturaPdfLayoutSupport.bancoExtraidoUtil(bancoExtraidoIa)) {
            return bancoExtraidoIa;
        }
        return FaturaPdfLayoutSupport.inferirBancoEmissorDoTexto(textoPdfNormalizado);
    }
}
