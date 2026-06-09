package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            && FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "itau",
                "itaú unibanco",
                "itaucard",
                "www itau com br"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT ITAÚ: padrão 'DD/MM estabelecimento … N/N valor' — o par N/N antes do valor "
            + "(ex.: '10/10 64,10') é parcela atual/total; o valor cobrado NESTA fatura é só o último número (64.10), "
            + "NUNCA 1064.10 por causa de '10/10'. Milhar só com ponto brasileiro explícito ('1.064,10'). "
            + "bancoCartao='Itaú'. Extraia só compras/parcelas/taxas do período; ignore limites, pontos e simulações.";
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
        return "Itaú";
    }
}
