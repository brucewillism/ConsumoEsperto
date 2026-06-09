package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InterFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.INTER;
    }

    @Override
    public int prioridade() {
        return 88;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            && FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "banco inter",
                "bancointer",
                "inter medium",
                "inter black",
                "inter gold"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT BANCO INTER: bancoCartao='Inter'. Transações em tabela com data no período da fatura. "
            + "Ignore 'Opções de pagamento', simulação de parcelamento da fatura, limites, cashback e rodapé. "
            + "PDF protegido por senha não deve gerar lançamentos inventados.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            String n = FaturaPdfLayoutSupport.norm(item.getDescricao());
            if (n.contains("opcoes de pagamento") || n.contains("parcelar fatura") || n.contains("limite disponivel")) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "Inter";
    }
}
