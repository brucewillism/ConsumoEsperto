package com.consumoesperto.service;

import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Saldo exibido no dashboard: receitas confirmadas − despesas confirmadas (inclui cartão na fatura).
 * O limite de crédito nunca é somado ao saldo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaldoService {

    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final OpenAiService openAiService;

    /**
     * Saldo = soma RECEITA confirmada − soma DESPESA confirmada.
     */
    public BigDecimal saldoContaCorrente(Long usuarioId) {
        return saldoConfirmado(usuarioId);
    }

    /**
     * Alias explícito (receitas − despesas confirmadas).
     */
    public BigDecimal saldoConfirmado(Long usuarioId) {
        BigDecimal r = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.RECEITA);
        BigDecimal d = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.DESPESA);
        BigDecimal i = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.INVESTIMENTO);
        r = r != null ? r : BigDecimal.ZERO;
        d = d != null ? d : BigDecimal.ZERO;
        i = i != null ? i : BigDecimal.ZERO;
        return r.subtract(d).subtract(i);
    }

    public void notificarAlteracaoSaldo(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        BigDecimal s = saldoContaCorrente(usuarioId);
        log.debug("[SALDO] Utilizador {} — saldo (receitas − despesas confirmadas): {}", usuarioId, s);
    }

    public Optional<AuditoriaLiquidez> analisarDinheiroParado(Long usuarioId) {
        BigDecimal saldo = saldoContaCorrente(usuarioId);
        if (saldo.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return Optional.empty();
        }
        LocalDateTime agora = LocalDateTime.now();
        List<Fatura> faturas = faturaRepository.findProximasNaoPagas(usuarioId, agora, agora.plusDays(30));
        if (faturas.isEmpty()) {
            return Optional.empty();
        }
        Fatura proxima = faturas.get(0);
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), proxima.getDataVencimento().toLocalDate());
        if (dias <= 5) {
            return Optional.empty();
        }
        BigDecimal valorAplicavel = saldo.min(proxima.getValorFatura() != null ? proxima.getValorFatura() : saldo);
        BigDecimal ganhoEstimado = valorAplicavel
            .multiply(BigDecimal.valueOf(0.001))
            .multiply(BigDecimal.valueOf(dias))
            .setScale(2, RoundingMode.HALF_UP);
        return Optional.of(new AuditoriaLiquidez(
            saldo.setScale(2, RoundingMode.HALF_UP),
            valorAplicavel.setScale(2, RoundingMode.HALF_UP),
            ganhoEstimado,
            proxima.getDataVencimento().toLocalDate(),
            Math.max(1, dias - 1)
        ));
    }

    public Optional<OportunidadeInvestimento> sugerirInvestimentoSaldo(Long usuarioId) {
        BigDecimal saldo = saldoContaCorrente(usuarioId);
        if (saldo.compareTo(BigDecimal.valueOf(500)) < 0) {
            return Optional.empty();
        }
        BigDecimal valor = saldo.min(BigDecimal.valueOf(10000)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal poupanca = rendimento(valor, "0.0055");
        BigDecimal selic = rendimento(valor, "0.0085");
        BigDecimal cdb = rendimento(valor, "0.0092");
        String melhor = cdb.compareTo(selic) >= 0 ? "CDB de liquidez diária" : "Tesouro Selic";
        String textoIa;
        try {
            textoIa = openAiService.gerarTexto(usuarioId,
                "Explique em português brasileiro, em até 3 frases, uma simulação educativa de investimento. "
                    + "Não prometa rentabilidade e diga que não é recomendação individual.",
                "Saldo ocioso R$ " + valor + ". Poupança R$ " + poupanca
                    + ", Tesouro Selic R$ " + selic + ", CDB liquidez diária R$ " + cdb + ".",
                "Esta é uma simulação educativa, não uma recomendação individual. Compare liquidez, risco e prazo antes de aplicar.");
        } catch (Exception e) {
            textoIa = "Esta é uma simulação educativa, não uma recomendação individual. Compare liquidez, risco e prazo antes de aplicar.";
        }
        return Optional.of(new OportunidadeInvestimento(valor, poupanca, selic, cdb, melhor, textoIa));
    }

    private static BigDecimal rendimento(BigDecimal valor, String taxaMensal) {
        return valor.multiply(new BigDecimal(taxaMensal)).setScale(2, RoundingMode.HALF_UP);
    }

    public record AuditoriaLiquidez(
        BigDecimal saldoDisponivel,
        BigDecimal valorAplicavel,
        BigDecimal ganhoEstimado,
        LocalDate vencimentoFatura,
        long diasParaResgate
    ) {
        public String mensagem() {
            return "Vi que você tem R$ " + saldoDisponivel + " parados. Se colocar R$ "
                + valorAplicavel + " num CDB hoje, pode ganhar aproximadamente R$ "
                + ganhoEstimado + " até o vencimento da sua fatura no dia "
                + vencimentoFatura.getDayOfMonth() + ". Quer que eu te lembre de resgatar no dia "
                + vencimentoFatura.minusDays(1).getDayOfMonth() + "?";
        }
    }

    public record OportunidadeInvestimento(
        BigDecimal saldoOcioso,
        BigDecimal rendimentoPoupanca,
        BigDecimal rendimentoTesouroSelic,
        BigDecimal rendimentoCdb,
        String melhorOpcao,
        String explicacaoIa
    ) {
        public String mensagemWhatsApp() {
            return "Você tem R$ " + saldoOcioso + " parados. Na Poupança renderia cerca de R$ "
                + rendimentoPoupanca + ". No Tesouro Selic renderia cerca de R$ " + rendimentoTesouroSelic
                + ". No CDB de liquidez diária renderia cerca de R$ " + rendimentoCdb
                + ". Melhor simulação do dia: *" + melhorOpcao
                + "*. Deseja simular o impacto disso no seu Score de Saúde Financeira?\n\n"
                + explicacaoIa;
        }
    }
}
