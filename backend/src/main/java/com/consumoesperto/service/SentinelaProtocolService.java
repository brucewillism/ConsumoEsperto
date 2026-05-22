package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Protocolo Sentinela — projeção com patrimônio multicarteira, colchão sazonal e alerta tático via IA.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SentinelaProtocolService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public enum NivelAlertaSentinela {
        OK, MODERADO, CRITICO
    }

    public record SentinelaMargemDTO(
        BigDecimal patrimonioLiquido,
        BigDecimal receitasPrevistas,
        BigDecimal despesasPrevistas,
        BigDecimal compromissosRecorrentes,
        BigDecimal novaDespesa,
        BigDecimal colchaoVirtual,
        BigDecimal saldoMarginal,
        BigDecimal saldoMarginalAjustado,
        NivelAlertaSentinela nivelAlerta,
        String descricaoColchao
    ) {}

    private final RecurringExpenseDetectionService recurringExpenseDetectionService;
    private final SaldoService saldoService;
    private final SentinelaBufferSazonalService sentinelaBufferSazonalService;
    private final OpenAiService openAiService;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public SentinelaMargemDTO calcularMargemSentinela(Transacao novaDespesa) {
        if (novaDespesa == null || novaDespesa.getUsuario() == null) {
            return vazio();
        }
        Long usuarioId = novaDespesa.getUsuario().getId();

        SaldoService.ProjecaoMesCaixa projecao = saldoService.calcularProjecaoMes(usuarioId);
        SentinelaBufferSazonalService.ColchaoSazonal colchao = sentinelaBufferSazonalService.calcularColchao(usuarioId);

        List<RecurringExpenseDetectionService.RecurringExpense> padroes =
            recurringExpenseDetectionService.detectar(usuarioId);
        BigDecimal somaFixas = padroes.stream()
            .map(RecurringExpenseDetectionService.RecurringExpense::valorMedio)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal receitasPrevistas = projecao.receitasPrevistasConsolidadas();
        BigDecimal nova = nz(novaDespesa.getValor());
        BigDecimal deltaNova = saldoService.deltaProjecaoNovaDespesa(novaDespesa);

        BigDecimal saldoMarginal = projecao.patrimonioLiquido()
            .add(receitasPrevistas)
            .subtract(projecao.despesasPrevistas())
            .subtract(somaFixas)
            .subtract(deltaNova)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal colchaoValor = colchao.valorTotal();
        BigDecimal saldoAjustado = saldoMarginal.add(colchaoValor).setScale(2, RoundingMode.HALF_UP);
        NivelAlertaSentinela nivel = classificarNivel(saldoMarginal, saldoAjustado);

        log.info(
            "[JARVIS-LOG] Sentinela margem userId={} patrimonio={} colchao={} saldoMarginal={} ajustado={} nivel={}",
            usuarioId, projecao.patrimonioLiquido(), colchaoValor, saldoMarginal, saldoAjustado, nivel);

        return new SentinelaMargemDTO(
            projecao.patrimonioLiquido(),
            receitasPrevistas,
            projecao.despesasPrevistas(),
            somaFixas,
            nova,
            colchaoValor,
            saldoMarginal,
            saldoAjustado,
            nivel,
            colchao.descricaoProxima()
        );
    }

    public Optional<String> gerarAlertaTaticoIfNegativo(Transacao transacao, SentinelaMargemDTO dto) {
        if (dto.nivelAlerta() == NivelAlertaSentinela.OK) {
            return Optional.empty();
        }
        if (dto.nivelAlerta() == NivelAlertaSentinela.MODERADO) {
            String msg = "*Aviso Sentinela (moderado)*\n"
                + "Margem bruta: *" + BRL.format(dto.saldoMarginal()) + "*, "
                + "mas o colchão sazonal (*" + BRL.format(dto.colchaoVirtual()) + "*) "
                + "de receita fiscal prevista cobre a diferença.\n"
                + (dto.descricaoColchao() != null ? "Próxima entrada: " + dto.descricaoColchao() + ".\n" : "")
                + "Mantenha disciplina até o crédito cair.";
            return Optional.of(msg);
        }

        Usuario u = usuarioRepository.findById(transacao.getUsuario().getId()).orElse(null);
        String persona = jarvisProtocolService.camadaPersonaCompletaParaIa(u);
        String system = persona
            + "O saldo marginal do Protocolo Sentinela ficou negativo mesmo após colchão sazonal. "
            + "Emita um *alerta tático* breve (máximo 5 linhas), calmo e acionável. "
            + "Responda via JSON {\"texto\":\"...\"} apenas.";

        String userPrompt = "Patrimônio: " + BRL.format(dto.patrimonioLiquido()) + ".\n"
            + "Colchão sazonal: " + BRL.format(dto.colchaoVirtual()) + ".\n"
            + "Saldo marginal bruto: " + BRL.format(dto.saldoMarginal()) + ".\n"
            + "Saldo marginal ajustado: " + BRL.format(dto.saldoMarginalAjustado()) + ".\n"
            + "Nova despesa: " + BRL.format(dto.novaDespesa()) + ".";

        String fallback = "Mesmo considerando receitas fiscais previstas, o Sentinela projeta saldo negativo: "
            + BRL.format(dto.saldoMarginalAjustado()) + ". Revise gastos variáveis.";
        String msg = openAiService.gerarTexto(transacao.getUsuario().getId(), system, userPrompt, fallback);
        return Optional.of(msg != null && !msg.isBlank() ? msg : fallback);
    }

    private static NivelAlertaSentinela classificarNivel(BigDecimal bruto, BigDecimal ajustado) {
        if (bruto.compareTo(BigDecimal.ZERO) >= 0) {
            return NivelAlertaSentinela.OK;
        }
        if (ajustado.compareTo(BigDecimal.ZERO) >= 0) {
            return NivelAlertaSentinela.MODERADO;
        }
        return NivelAlertaSentinela.CRITICO;
    }

    private static SentinelaMargemDTO vazio() {
        return new SentinelaMargemDTO(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO,
            NivelAlertaSentinela.OK, null
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
