package com.consumoesperto.service.motor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Recomendações educacionais — informativas, sem indicação personalizada de investimento.
 */
public final class AdvisorInvestimentoEngine {

    public enum PerfilInvestidor { CONSERVADOR, MODERADO, ARROJADO }

    public enum ProdutoEducativo {
        RESERVA_EMERGENCIA,
        TESOURO_SELIC,
        CDB,
        CDI,
        CONTA_REMUNERADA
    }

    public record Recomendacao(
        PerfilInvestidor perfilInvestidor,
        List<ProdutoEducativo> produtosCompativeis,
        String textoDeterministico,
        String avisoLegal
    ) {}

    private static final String AVISO =
        "Conteúdo educacional. Não constitui recomendação personalizada de investimento "
            + "nem oferta de produto financeiro.";

    private AdvisorInvestimentoEngine() {}

    public static Recomendacao recomendar(
        PerfilComportamentalEngine.Perfil perfilComportamental,
        MotorFinanceiroSnapshot s
    ) {
        PerfilInvestidor pi = mapearPerfil(perfilComportamental, s);
        List<ProdutoEducativo> produtos = produtosPara(pi, s);
        String texto = montarTexto(pi, s, produtos);
        return new Recomendacao(pi, produtos, texto, AVISO);
    }

    private static PerfilInvestidor mapearPerfil(
        PerfilComportamentalEngine.Perfil pc,
        MotorFinanceiroSnapshot s
    ) {
        if (pc == PerfilComportamentalEngine.Perfil.CONSERVADOR
            || pc == PerfilComportamentalEngine.Perfil.RENDA_VARIAVEL) {
            return PerfilInvestidor.CONSERVADOR;
        }
        if (pc == PerfilComportamentalEngine.Perfil.IMPULSIVO) {
            return PerfilInvestidor.CONSERVADOR;
        }
        BigDecimal meses = s.mesesReserva() != null ? s.mesesReserva() : BigDecimal.ZERO;
        if (meses.compareTo(BigDecimal.valueOf(6)) >= 0
            && s.utilizacaoCreditoPct().compareTo(BigDecimal.valueOf(40)) <= 0) {
            return PerfilInvestidor.MODERADO;
        }
        return PerfilInvestidor.CONSERVADOR;
    }

    private static List<ProdutoEducativo> produtosPara(PerfilInvestidor pi, MotorFinanceiroSnapshot s) {
        List<ProdutoEducativo> list = new ArrayList<>();
        BigDecimal meses = s.mesesReserva() != null ? s.mesesReserva() : BigDecimal.ZERO;
        if (meses.compareTo(BigDecimal.valueOf(3)) < 0) {
            list.add(ProdutoEducativo.RESERVA_EMERGENCIA);
            list.add(ProdutoEducativo.CONTA_REMUNERADA);
            return list;
        }
        list.add(ProdutoEducativo.TESOURO_SELIC);
        list.add(ProdutoEducativo.CDB);
        list.add(ProdutoEducativo.CDI);
        if (pi == PerfilInvestidor.MODERADO && meses.compareTo(BigDecimal.valueOf(6)) >= 0) {
            list.add(ProdutoEducativo.CONTA_REMUNERADA);
        }
        return list;
    }

    private static String montarTexto(
        PerfilInvestidor pi,
        MotorFinanceiroSnapshot s,
        List<ProdutoEducativo> produtos
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Perfil de investimento educacional: ").append(pi.name()).append(". ");
        BigDecimal meses = s.mesesReserva() != null ? s.mesesReserva() : BigDecimal.ZERO;
        if (meses.compareTo(BigDecimal.valueOf(3)) < 0) {
            sb.append("Com reserva abaixo de 3 meses, priorize reserva de emergência líquida ");
            sb.append("antes de buscar rentabilidade. ");
        } else {
            sb.append("Com reserva de ").append(meses.setScale(1, java.math.RoundingMode.HALF_UP))
                .append(" meses, produtos de liquidez diária e baixo risco são compatíveis com perfil ")
                .append(pi.name().toLowerCase()).append(". ");
        }
        sb.append("Produtos mencionados para estudo: ");
        sb.append(produtos.stream().map(ProdutoEducativo::name).reduce((a, b) -> a + ", " + b).orElse("—"));
        sb.append(". ");
        if (pi == PerfilInvestidor.CONSERVADOR) {
            sb.append("Exemplo educacional: Tesouro Selic ou CDB com liquidez diária próximo de 100% do CDI.");
        }
        return sb.toString();
    }
}
