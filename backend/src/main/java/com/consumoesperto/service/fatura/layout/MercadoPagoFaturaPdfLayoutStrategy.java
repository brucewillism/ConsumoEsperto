package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class MercadoPagoFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.MERCADO_PAGO;
    }

    @Override
    public int prioridade() {
        return 89;
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
            if (!MercadoPagoFaturaTextoExtrator.deveIgnorarDescricao(item.getDescricao())) {
                out.add(item);
            }
        }
        return out;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "Mercado Pago";
    }

    @Override
    public void complementarLancamentosDoTexto(String textoPdf, List<ImportacaoFaturaItemDTO> itens, int anoReferencia) {
        MercadoPagoFaturaTextoExtrator.complementar(itens, textoPdf, anoReferencia);
    }

    @Override
    public Optional<BigDecimal> extrairTotalFaturaDoTexto(String textoPdf) {
        return MercadoPagoFaturaTextoExtrator.extrairTotalFatura(textoPdf);
    }

    @Override
    public void finalizarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        MercadoPagoFaturaTextoExtrator.finalizarLista(itens, textoPdf, totalFatura, anoReferencia);
    }
}
