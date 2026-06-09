package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MercadoPagoFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.MERCADO_PAGO;
    }

    @Override
    public int prioridade() {
        return 87;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            && FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "mercado pago",
                "mercadopago",
                "movimentacoes na fatura",
                "credit card mp"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT MERCADO PAGO: bancoCartao='Mercado Pago'. Lançamentos reais em 'Movimentações na fatura' "
            + "(colunas Data/Movimentações/Valor). 'Resumo da fatura', 'Consumos', 'Tarifas', "
            + "'Total da fatura de', 'Pagamentos e créditos devolvidos' são resumo — NÃO liste como lançamento. "
            + "Ignore simulações '1 + [9]x', parcelamentos de fatura ativos e rodapé SAC.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            String n = FaturaPdfLayoutSupport.norm(item.getDescricao());
            if (n.contains("resumo da fatura") || n.contains("consumos de") || n.contains("tarifas e encargos")
                || n.contains("pagamentos e creditos devolvidos") || n.contains("total da fatura de")) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "Mercado Pago";
    }
}
