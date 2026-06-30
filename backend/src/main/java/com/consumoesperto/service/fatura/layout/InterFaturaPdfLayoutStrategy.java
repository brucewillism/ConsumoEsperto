package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        if (FaturaPdfLayoutSupport.contem(
            textoPdfNormalizado,
            "mercado pago",
            "mercadopago",
            "nubank",
            "nu pagamentos",
            "itau",
            "itaú unibanco"
        )) {
            return false;
        }
        return FaturaPdfLayoutSupport.contem(
            textoPdfNormalizado,
            "banco inter",
            "bancointer",
            "inter medium",
            "inter black",
            "inter gold",
            "super app inter"
        ) && FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado);
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT BANCO INTER: bancoCartao='Inter'. Extraia SOMENTE o bloco «Detalhamento da fatura» "
            + "(compras/parcelas do período até a data de corte). "
            + "NUNCA inclua «Próximas faturas» (parcelas futuras do mesmo plano) nem «Opções de pagamento» "
            + "(simulações tipo '1 + 5x R$ 71,81', CET, IOF rotativo, valor financiado). "
            + "Ignore a linha-resumo «Despesas do mês» e comprovantes «Pagamento on line» — não são compras. "
            + "Se a fatura já estiver paga (Valor da fatura R$ 0,00), extraia cada compra individual do detalhamento. "
            + "Parcela no texto: 'Parcela 04 de 06' = parcelaAtual 4, totalParcelas 6. "
            + "valorTotal = 'Valor da fatura' do resumo. PDF protegido: senha = 6 primeiros dígitos do CPF.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            String n = FaturaPdfLayoutSupport.norm(item.getDescricao());
            if (InterFaturaTextoExtrator.deveIgnorarDescricao(item.getDescricao())
                || InterFaturaTextoExtrator.pareceLinhaEncargoInter(item.getDescricao())
                || InterFaturaTextoExtrator.pareceLinhaSimulacaoTaxaInter(item.getDescricao(), item.getValor())
                || FaturaPdfLayoutSupport.pareceDescricaoGenericaIa(item.getDescricao())
                || InterFaturaTextoExtrator.descricaoInvalidaPublica(item.getDescricao())
                || n.contains("parcelar fatura")
                || n.contains("limite disponivel")
                || n.contains("limite total")
                || n.contains("limite utilizado")
                || n.contains("valor financiado")
                || n.contains("taxa efetiva")
                || n.matches(".*\\d\\s*\\+\\s*\\d+x.*")) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    @Override
    public void complementarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        int anoReferencia
    ) {
        InterFaturaTextoExtrator.complementar(itens, textoPdf, anoReferencia);
    }

    @Override
    public Optional<BigDecimal> extrairTotalFaturaDoTexto(String textoPdf) {
        return InterFaturaTextoExtrator.extrairTotalFatura(textoPdf);
    }

    @Override
    public void finalizarLancamentosDoTexto(
        String textoPdf,
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        InterFaturaTextoExtrator.finalizarListaInter(itens, textoPdf, totalFatura, anoReferencia);
    }

    @Override
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "Inter";
    }
}
