package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Faturas com bandeira Mastercard (emissor pode ser Itaú, Santander, etc.).
 * A detecção do emissor fino fica em {@link #sugerirBancoCartao(String, String)}.
 */
@Component
public class MastercardFaturaPdfLayoutStrategy implements FaturaPdfLayoutStrategy {

    @Override
    public BancoFaturaLayout layout() {
        return BancoFaturaLayout.MASTERCARD;
    }

    @Override
    public int prioridade() {
        return 75;
    }

    @Override
    public boolean reconhece(String textoPdfNormalizado) {
        if (!FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)) {
            return false;
        }
        if (FaturaPdfLayoutSupport.contem(
            textoPdfNormalizado,
            "itau", "nubank", "banco inter", "mercado pago", "banco do brasil",
            "bradesco", "santander", "caixa economica", "c6 bank", "xp investimentos", "banco do nordeste"
        )) {
            return false;
        }
        return FaturaPdfLayoutSupport.contem(
            textoPdfNormalizado,
            "mastercard",
            "fatura mastercard",
            "cartao master"
        );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT MASTERCARD: identifique o banco EMISSOR no cabeçalho (Itaú, Santander, Bradesco, etc.) "
            + "e preencha bancoCartao com o emissor, não com 'Mastercard'. "
            + "Extraia só compras/parcelas/taxas do período; ignore limites, pontos, milhas e simulações de parcelamento. "
            + "Padrão parcela N/N antes do valor = parcela do mês, não milhar.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            String n = FaturaPdfLayoutSupport.norm(item.getDescricao());
            if (n.contains("programa de recompensa") || n.contains("pontos") || n.contains("milhas")
                || n.contains("limite total")) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        if (FaturaPdfLayoutSupport.contem(textoPdfNormalizado, "itau", "itaú unibanco")) {
            return "Itaú";
        }
        if (FaturaPdfLayoutSupport.contem(textoPdfNormalizado, "santander")) {
            return "Santander";
        }
        if (FaturaPdfLayoutSupport.contem(textoPdfNormalizado, "bradesco")) {
            return "Bradesco";
        }
        if (FaturaPdfLayoutSupport.contem(textoPdfNormalizado, "banco do brasil", "bb ")) {
            return "Banco do Brasil";
        }
        if (bancoExtraidoIa != null && !bancoExtraidoIa.isBlank()) {
            return bancoExtraidoIa;
        }
        return "Mastercard";
    }
}
