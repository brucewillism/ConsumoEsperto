package com.consumoesperto.service;

import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.DebitoInterno;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.DebitoInternoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Monta o bloco "CONTEXTO ATUAL" injetado no system prompt da persona J.A.R.V.I.S.
 * (saldo do mês, orçamentos críticos/estourados, mês de referência).
 *
 * <p>Todas as fontes têm fallback seguro: qualquer falha resulta em valor neutro,
 * nunca num placeholder literal a chegar ao modelo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JarvisContextoFinanceiroService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final Locale PT_BR = new Locale("pt", "BR");

    private final OrcamentoService orcamentoService;
    private final SaldoService saldoService;
    private final UsuarioRepository usuarioRepository;
    private final JarvisProtocolService jarvisProtocolService;
    private final ContaBancariaRepository contaBancariaRepository;
    private final DebitoInternoRepository debitoInternoRepository;
    private final AgendamentoPagamentoService agendamentoPagamentoService;

    /** Bloco textual pronto para anexar ao system prompt. Nunca lança exceção. */
    public String montarBlocoContexto(Long userId) {
        Usuario usuario = userId == null ? null : usuarioRepository.findById(userId).orElse(null);
        String nome = jarvisProtocolService.extrairPrimeiroNome(usuario);
        if (nome == null || nome.isBlank()) {
            nome = "o usuário";
        }
        String tratamento = jarvisProtocolService.tratamentoConversacional(usuario);

        String saldoMes = formatarSaldoSeguro(userId);
        List<String> criticas = new ArrayList<>();
        List<String> estouradas = new ArrayList<>();
        preencherOrcamentos(userId, criticas, estouradas);

        YearMonth ym = YearMonth.now();
        String mesRef = ym.getMonth().getDisplayName(TextStyle.FULL, PT_BR) + "/" + ym.getYear();

        StringBuilder sb = new StringBuilder();
        sb.append("CONTEXTO ATUAL\n");
        sb.append("- Usuário: ").append(nome).append("\n");
        sb.append("- Tratamento: ").append(tratamento).append("\n");
        sb.append("- Saldo disponível estimado no mês: ").append(saldoMes).append("\n");
        sb.append("- Categorias com orçamento crítico (>80%): ")
            .append(criticas.isEmpty() ? "(nenhuma)" : String.join(", ", criticas)).append("\n");
        sb.append("- Categorias com orçamento estourado (>100%): ")
            .append(estouradas.isEmpty() ? "(nenhuma)" : String.join(", ", estouradas)).append("\n");
        List<String> linhasContas = montarLinhasContas(userId);
        if (!linhasContas.isEmpty()) {
            sb.append("- Contas e cheque especial:\n");
            for (String linha : linhasContas) {
                sb.append("  • ").append(linha).append("\n");
            }
        }
        String debitos = montarBlocoDebitosInternos(userId);
        if (!debitos.isBlank()) {
            sb.append(debitos);
        }
        String agendamentos = montarBlocoAgendamentos(userId);
        if (!agendamentos.isBlank()) {
            sb.append(agendamentos);
        }
        sb.append("- Mês de referência: ").append(mesRef).append("\n");
        return sb.toString();
    }

    /** Resumo dos débitos internos (racha-contas) pendentes do usuário no grupo familiar. */
    private String montarBlocoDebitosInternos(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            List<DebitoInterno> aReceber = debitoInternoRepository.findAReceber(userId);
            List<DebitoInterno> devidos = debitoInternoRepository.findDevidos(userId);
            if (aReceber.isEmpty() && devidos.isEmpty()) {
                return "";
            }
            BigDecimal totalReceber = aReceber.stream().map(DebitoInterno::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDevido = devidos.stream().map(DebitoInterno::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            StringBuilder sb = new StringBuilder();
            sb.append("- Racha-contas (grupo familiar): a receber ").append(BRL.format(totalReceber))
                .append(", a pagar ").append(BRL.format(totalDevido)).append("\n");
            return sb.toString();
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: débitos internos indisponíveis userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    /**
     * Linhas de contexto por conta — destaca uso de cheque especial para o J.A.R.V.I.S.
     * Contas sem cheque especial e com saldo positivo não geram menção (cenário E).
     */
    private List<String> montarLinhasContas(Long userId) {
        List<String> linhas = new ArrayList<>();
        if (userId == null) {
            return linhas;
        }
        try {
            List<ContaBancaria> contas = contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(userId);
            for (ContaBancaria conta : contas) {
                if (conta == null || conta.getSaldoAtual() == null) {
                    continue;
                }
                BigDecimal saldo = conta.getSaldoAtual();
                BigDecimal limite = conta.getLimiteChequeEspecial();
                boolean temCheque = limite.compareTo(BigDecimal.ZERO) > 0;

                if (saldo.compareTo(BigDecimal.ZERO) >= 0) {
                    if (temCheque) {
                        linhas.add(String.format(
                            "Conta %s: Saldo %s (cheque especial disponível: %s — não utilizado)",
                            conta.getNome(), BRL.format(saldo), BRL.format(limite)));
                    }
                } else {
                    BigDecimal utilizado = conta.getChequeEspecialUtilizado();
                    BigDecimal disponivelRestante = conta.getSaldoDisponivel();
                    if (temCheque) {
                        linhas.add(String.format(
                            "Conta %s: Saldo %s (ATENÇÃO: utilizando %s do cheque especial de %s. "
                                + "Limite restante disponível: %s)",
                            conta.getNome(), BRL.format(saldo), BRL.format(utilizado),
                            BRL.format(limite), BRL.format(disponivelRestante)));
                    } else {
                        linhas.add(String.format(
                            "Conta %s: Saldo %s (ATENÇÃO: saldo negativo, sem cheque especial configurado)",
                            conta.getNome(), BRL.format(saldo)));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: contas indisponíveis userId={}: {}", userId, e.getMessage());
        }
        return linhas;
    }

    private String montarBlocoAgendamentos(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            BigDecimal total = agendamentoPagamentoService.totalAgendadoFuturo(userId);
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                return "";
            }
            return "- Pagamentos agendados (saídas previstas): " + BRL.format(total) + " em boletos/Pix futuros\n";
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: agendamentos indisponíveis userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    private String formatarSaldoSeguro(Long userId) {
        if (userId == null) {
            return "(indisponível)";
        }
        try {
            BigDecimal saldo = saldoService.saldoConfirmado(userId);
            return saldo != null ? BRL.format(saldo) : "(indisponível)";
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: saldo indisponível userId={}: {}", userId, e.getMessage());
            return "(indisponível)";
        }
    }

    private void preencherOrcamentos(Long userId, List<String> criticas, List<String> estouradas) {
        if (userId == null) {
            return;
        }
        try {
            List<OrcamentoDTO> orcamentos = orcamentoService.listar(userId, null, null);
            for (OrcamentoDTO o : orcamentos) {
                if (o == null || o.getPercentualUso() == null) {
                    continue;
                }
                double pct = o.getPercentualUso().doubleValue();
                String nome = o.getCategoriaNome() != null ? o.getCategoriaNome() : "categoria";
                if (pct >= 100.0) {
                    estouradas.add(nome);
                } else if (pct >= 80.0) {
                    criticas.add(nome);
                }
            }
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: orçamentos indisponíveis userId={}: {}", userId, e.getMessage());
        }
    }
}
