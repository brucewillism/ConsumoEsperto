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
        boolean sinaisInter = FaturaPdfLayoutSupport.contem(
            textoPdfNormalizado,
            "banco inter",
            "bancointer",
            "inter medium",
            "inter black",
            "inter gold",
            "super app inter",
            "resumo da fatura"
        );
        if (!sinaisInter) {
            return false;
        }
        return FaturaPdfLayoutSupport.pareceFaturaCartao(textoPdfNormalizado)
            || FaturaPdfLayoutSupport.contem(
                textoPdfNormalizado,
                "valor da fatura",
                "detalhamento da fatura",
                "proximas faturas",
                "opcoes de pagamento"
            );
    }

    @Override
    public String instrucoesExtracaoIa() {
        return "LAYOUT BANCO INTER: bancoCartao='Inter'. Extraia SOMENTE o bloco «Detalhamento da fatura» "
            + "(compras/parcelas do período até a data de corte). "
            + "NUNCA inclua «Próximas faturas» (parcelas futuras do mesmo plano) nem «Opções de pagamento» "
            + "(simulações tipo '1 + 5x R$ 71,81', CET, IOF rotativo, valor financiado). "
            + "Parcela no texto: 'Parcela 04 de 06' = parcelaAtual 4, totalParcelas 6. "
            + "valorTotal = 'Valor da fatura' do resumo. PDF protegido: senha = 6 primeiros dígitos do CPF.";
    }

    @Override
    public List<ImportacaoFaturaItemDTO> sanitizarLancamentos(List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            String n = FaturaPdfLayoutSupport.norm(item.getDescricao());
            if (InterFaturaTextoExtrator.deveIgnorarDescricao(item.getDescricao())
                || n.contains("parcelar fatura")
                || n.contains("limite disponivel")
                || n.contains("valor financiado")
                || n.contains("taxa efetiva")
                || n.contains("encargos rotativos")
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
    public String sugerirBancoCartao(String textoPdfNormalizado, String bancoExtraidoIa) {
        return "Inter";
    }
}
