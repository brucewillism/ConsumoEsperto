package com.consumoesperto.service;

import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Protocolo Sentinela — projeção com padrões de assinatura/recorrência e alerta tático via IA.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SentinelaProtocolService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public record SentinelaMargemDTO(
        BigDecimal receitasMes,
        BigDecimal somaAssinaturasEstimadas,
        BigDecimal novaDespesa,
        BigDecimal saldoMarginal
    ) {}

    private final RecurringExpenseDetectionService recurringExpenseDetectionService;
    private final RendaConfigService rendaConfigService;
    private final TransacaoRepository transacaoRepository;
    private final OpenAiService openAiService;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public SentinelaMargemDTO calcularMargemSentinela(Transacao novaDespesa) {
        if (novaDespesa == null || novaDespesa.getUsuario() == null) {
            return new SentinelaMargemDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        Long usuarioId = novaDespesa.getUsuario().getId();
        YearMonth ym = YearMonth.now();
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fimMes = ym.atEndOfMonth().atTime(23, 59, 59);

        BigDecimal receitas = rendaConfigService.obterDto(usuarioId)
            .map(RendaConfigDTO::getSalarioLiquido)
            .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
            .orElseGet(() -> nz(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, inicio, fimMes)));

        List<RecurringExpenseDetectionService.RecurringExpense> padroes =
            recurringExpenseDetectionService.detectar(usuarioId);
        BigDecimal somaFixas = padroes.stream()
            .map(RecurringExpenseDetectionService.RecurringExpense::valorMedio)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal nova = nz(novaDespesa.getValor());
        BigDecimal saldoMarginal = receitas.subtract(somaFixas).subtract(nova).setScale(2, RoundingMode.HALF_UP);

        log.info(
            "[JARVIS-LOG] Sentinela margem userId={} receitas={} fixasEstimadas={} novaDespesa={} saldoMarginal={}",
            usuarioId, receitas, somaFixas, nova, saldoMarginal);
        return new SentinelaMargemDTO(receitas, somaFixas, nova, saldoMarginal);
    }

    public Optional<String> gerarAlertaTaticoIfNegativo(Transacao transacao, SentinelaMargemDTO dto) {
        if (dto.saldoMarginal().compareTo(BigDecimal.ZERO) >= 0) {
            return Optional.empty();
        }
        Usuario u = usuarioRepository.findById(transacao.getUsuario().getId()).orElse(null);
        String persona = jarvisProtocolService.camadaPersonaCompletaParaIa(u);
        String system = persona
            + "O saldo marginal simplificado (receitas do mês menos padrões fixos/recorrentes estimados menos a despesa recém registada) "
            + "ficou negativo. Emita um *alerta tático* breve (máximo 5 linhas), calmo e acionável. "
            + "Indique risco de fecho de mês. Sem alarmismo. Responda via JSON {\"texto\":\"...\"} apenas.";

        String userPrompt = "Receitas consideradas: " + BRL.format(dto.receitasMes()) + ".\n"
            + "Recorrências/assinaturas estimadas (soma das médias detectadas no histórico): " + BRL.format(dto.somaAssinaturasEstimadas()) + ".\n"
            + "Valor da nova despesa: " + BRL.format(dto.novaDespesa()) + ".\n"
            + "Saldo marginal após esta despesa (receitas − fixas estimadas − nova despesa): " + BRL.format(dto.saldoMarginal()) + ".";

        String fallback = "Com este lançamento, o indicador Sentinela ficou negativo: receitas menos compromissos recorrentes estimados "
            + "menos esta despesa resultam em " + BRL.format(dto.saldoMarginal()) + ". Sugiro rever gastos variáveis e próximas recorrências.";
        String msg = openAiService.gerarTexto(transacao.getUsuario().getId(), system, userPrompt, fallback);
        log.info("[JARVIS-LOG] Sentinela alerta tático emitido userId={}", transacao.getUsuario().getId());
        return Optional.of(msg != null && !msg.isBlank() ? msg : fallback);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
