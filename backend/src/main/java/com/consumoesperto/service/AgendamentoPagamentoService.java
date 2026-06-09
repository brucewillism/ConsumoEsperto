package com.consumoesperto.service;

import com.consumoesperto.dto.AgendamentoPagamentoDTO;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.AgendamentoPagamento;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AgendamentoPagamentoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Agendamento de pagamentos de boleto/Pix — proposta via J.A.R.V.I.S., execução no vencimento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgendamentoPagamentoService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter FMT_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int SCALE = 2;
    private static final int SESSAO_TTL_MIN = 30;

    private final AgendamentoPagamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContaBancariaService contaBancariaService;
    private final TransacaoService transacaoService;
    private final UsuarioSessaoContextoService sessaoContextoService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    /** Dados normalizados extraídos de boleto/Pix. */
    public record DadosPagamento(
        String tipo,
        String beneficiario,
        BigDecimal valor,
        LocalDate dataVencimento,
        String codigoBarrasOuPix
    ) {}

    public boolean deveProporAgendamento(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        String erro = node.path("erro").asText("").trim();
        if (!erro.isBlank()) {
            return false;
        }
        double conf = node.path("confianca").asDouble(0);
        if (conf < 0.45) {
            return false;
        }
        BigDecimal valor = lerValor(node);
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        LocalDate venc = lerDataVencimento(node);
        return venc != null && !venc.isBefore(LocalDate.now());
    }

    /** Salva proposta na sessão e retorna mensagem para o usuário confirmar (sim/não). */
    @Transactional
    public String salvarPropostaESugerir(Long usuarioId, JsonNode extracted) {
        DadosPagamento dados = normalizarExtracao(extracted);
        validarParaAgendamento(dados);

        ContaBancaria conta = contaBancariaService.resolverContaParaTransacao(usuarioId, null);
        if (conta == null) {
            throw new IllegalArgumentException(
                "Cadastre uma conta bancária antes de agendar pagamentos (menu Contas).");
        }

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("tipo", dados.tipo());
        ctx.put("beneficiario", dados.beneficiario());
        ctx.put("valor", dados.valor());
        ctx.put("dataVencimento", dados.dataVencimento().toString());
        ctx.put("codigoBarrasOuPix", dados.codigoBarrasOuPix());
        ctx.put("contaBancariaId", conta.getId());
        ctx.put("contaBancariaNome", conta.getNome());
        sessaoContextoService.salvar(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_AGENDAMENTO_PAGAMENTO,
            ctx,
            SESSAO_TTL_MIN
        );

        return montarMensagemProposta(conta, dados);
    }

    @Transactional
    public String confirmarAgendamento(Long usuarioId) {
        Map<String, Object> ctx = sessaoContextoService.buscarAtiva(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_AGENDAMENTO_PAGAMENTO
        ).orElseThrow(() -> new IllegalArgumentException("Não há agendamento pendente de confirmação."));

        sessaoContextoService.remover(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_AGENDAMENTO_PAGAMENTO
        );

        DadosPagamento dados = dadosFromContexto(ctx);
        validarParaAgendamento(dados);

        Long contaId = ctx.get("contaBancariaId") instanceof Number n ? n.longValue() : null;
        ContaBancaria conta = contaBancariaService.buscarEntidade(
            contaId != null ? contaId : resolverContaId(ctx),
            usuarioId
        );

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        AgendamentoPagamento ag = new AgendamentoPagamento();
        ag.setUsuario(usuario);
        ag.setContaDebito(conta);
        ag.setBeneficiario(dados.beneficiario());
        ag.setValor(dados.valor());
        ag.setDataVencimento(dados.dataVencimento());
        ag.setCodigoBarrasOuPix(dados.codigoBarrasOuPix());
        ag.setStatus(AgendamentoPagamento.StatusAgendamento.AGENDADO);
        AgendamentoPagamento salvo = agendamentoRepository.save(ag);

        return "Agendamento confirmado! Vou debitar *" + BRL.format(salvo.getValor())
            + "* da conta *" + conta.getNome() + "* no dia *"
            + salvo.getDataVencimento().format(FMT_BR) + "* (beneficiário: *"
            + salvo.getBeneficiario() + "*). Acompanhe em *Transações → Pagamentos agendados*.";
    }

    @Transactional
    public void cancelarProposta(Long usuarioId) {
        sessaoContextoService.remover(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_AGENDAMENTO_PAGAMENTO
        );
    }

    public boolean temPropostaPendente(Long usuarioId) {
        return sessaoContextoService.buscarAtiva(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_AGENDAMENTO_PAGAMENTO
        ).isPresent();
    }

    @Transactional(readOnly = true)
    public List<AgendamentoPagamentoDTO> listar(Long usuarioId) {
        return agendamentoRepository.findByUsuarioIdOrderByVencimento(usuarioId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public AgendamentoPagamentoDTO cancelar(Long usuarioId, Long agendamentoId) {
        AgendamentoPagamento ag = agendamentoRepository.findByIdAndUsuarioId(agendamentoId, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));
        if (ag.getStatus() != AgendamentoPagamento.StatusAgendamento.AGENDADO) {
            throw new IllegalStateException("Só é possível cancelar agendamentos com status AGENDADO.");
        }
        ag.setStatus(AgendamentoPagamento.StatusAgendamento.CANCELADO);
        ag.setDataProcessamento(LocalDateTime.now());
        return toDto(agendamentoRepository.save(ag));
    }

    /** Job diário: executa agendamentos com vencimento hoje. */
    @Scheduled(cron = "0 0 6 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void executarPagamentosDoDia() {
        LocalDate hoje = LocalDate.now();
        List<AgendamentoPagamento> pendentes = agendamentoRepository.findByStatusAndDataVencimento(
            AgendamentoPagamento.StatusAgendamento.AGENDADO, hoje);
        if (pendentes.isEmpty()) {
            return;
        }
        log.info("[AGENDAMENTO] Processando {} pagamento(s) com vencimento {}", pendentes.size(), hoje);
        for (AgendamentoPagamento ag : pendentes) {
            try {
                processarUm(ag);
            } catch (Exception e) {
                log.warn("[AGENDAMENTO] Falha id={}: {}", ag.getId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal totalAgendadoFuturo(Long usuarioId) {
        BigDecimal sum = agendamentoRepository.sumAgendadosFuturos(usuarioId, LocalDate.now());
        return sum != null ? sum.setScale(SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private void processarUm(AgendamentoPagamento ag) {
        Long usuarioId = ag.getUsuario().getId();
        ContaBancaria conta = contaBancariaService.buscarEntidade(ag.getContaDebito().getId(), usuarioId);

        if (!conta.temSaldoSuficiente(ag.getValor())) {
            ag.setStatus(AgendamentoPagamento.StatusAgendamento.FALHOU);
            ag.setDataProcessamento(LocalDateTime.now());
            ag.setMensagemErro("Saldo insuficiente (incl. cheque especial). Disponível: "
                + conta.getSaldoDisponivel().setScale(SCALE, RoundingMode.HALF_UP));
            agendamentoRepository.save(ag);
            notificarFalha(ag, conta);
            return;
        }

        TransacaoDTO tx = new TransacaoDTO();
        tx.setDescricao("Pagamento agendado: " + ag.getBeneficiario());
        tx.setValor(ag.getValor());
        tx.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
        tx.setDataTransacao(LocalDateTime.now());
        tx.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        tx.setContaBancariaId(conta.getId());
        transacaoService.criarTransacao(tx, usuarioId, false);

        ag.setStatus(AgendamentoPagamento.StatusAgendamento.PAGO);
        ag.setDataProcessamento(LocalDateTime.now());
        agendamentoRepository.save(ag);
        log.info("[AGENDAMENTO] Pago id={} userId={} valor={}", ag.getId(), usuarioId, ag.getValor());
    }

    private void notificarFalha(AgendamentoPagamento ag, ContaBancaria conta) {
        try {
            Long userId = ag.getUsuario().getId();
            String msg = "Não consegui pagar o agendamento de *" + BRL.format(ag.getValor())
                + "* para *" + ag.getBeneficiario() + "* (vencimento hoje). "
                + "Saldo disponível na conta *" + conta.getNome() + "*: *"
                + BRL.format(conta.getSaldoDisponivel()) + "*. "
                + "Faça um Pix para a conta ou reagende no app.";
            whatsAppNotificationService.enviarParaUsuario(userId, msg);
        } catch (Exception e) {
            log.debug("Falha ao notificar agendamento: {}", e.getMessage());
        }
    }

    private String montarMensagemProposta(ContaBancaria conta, DadosPagamento dados) {
        StringBuilder sb = new StringBuilder();
        sb.append("Chefe, identifiquei um ");
        sb.append("PIX".equalsIgnoreCase(dados.tipo()) ? "*Pix*" : "*boleto*");
        sb.append(" da *").append(dados.beneficiario()).append("* de *")
            .append(BRL.format(dados.valor())).append("* para o dia *")
            .append(dados.dataVencimento().format(FMT_BR)).append("*.\n\n");

        BigDecimal disponivel = conta.getSaldoDisponivel();
        boolean cobre = conta.temSaldoSuficiente(dados.valor());
        if (cobre) {
            BigDecimal saldo = conta.getSaldoAtual() != null ? conta.getSaldoAtual() : BigDecimal.ZERO;
            if (saldo.compareTo(dados.valor()) >= 0) {
                sb.append("Sua conta *").append(conta.getNome()).append("* tem saldo suficiente para cobrir ");
                sb.append("sem ativar o cheque especial.\n");
            } else if (conta.getLimiteChequeEspecial().compareTo(BigDecimal.ZERO) > 0) {
                sb.append("Com o saldo atual, esse pagamento pode usar até *")
                    .append(BRL.format(dados.valor().subtract(saldo.max(BigDecimal.ZERO))))
                    .append("* do cheque especial da conta *").append(conta.getNome()).append("*.\n");
            } else {
                sb.append("Saldo disponível na conta *").append(conta.getNome()).append("*: *")
                    .append(BRL.format(disponivel)).append("*.\n");
            }
        } else {
            sb.append("Atenção: hoje o disponível na conta *").append(conta.getNome())
                .append("* é *").append(BRL.format(disponivel))
                .append("* — no vencimento o sistema tentará debitar; se não houver saldo, marcará como falhou.\n");
        }
        sb.append("\n**Posso agendar?** Responda *sim* ou *não*.");
        return sb.toString();
    }

    private DadosPagamento normalizarExtracao(JsonNode node) {
        String tipo = node.path("tipo").asText(
            node.path("tipoDocumento").asText("BOLETO")).trim().toUpperCase(Locale.ROOT);
        if (tipo.contains("PIX")) {
            tipo = "PIX";
        } else if (tipo.contains("BOLETO")) {
            tipo = "BOLETO";
        }
        String beneficiario = node.path("beneficiario").asText(
            node.path("beneficiarioNome").asText("Beneficiário")).trim();
        if (beneficiario.isBlank()) {
            beneficiario = "Beneficiário";
        }
        BigDecimal valor = lerValor(node);
        LocalDate venc = lerDataVencimento(node);
        String codigo = higienizarCodigo(
            node.path("codigoBarrasOuPix").asText(
                node.path("codigoPix").asText(
                    node.path("linhaDigitavel").asText(
                        node.path("payloadPix").asText("")))));
        return new DadosPagamento(tipo, beneficiario, valor, venc, codigo);
    }

    private void validarParaAgendamento(DadosPagamento dados) {
        if (dados.valor() == null || dados.valor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Não identifiquei o valor do boleto/Pix com segurança.");
        }
        if (dados.dataVencimento() == null) {
            throw new IllegalArgumentException("Não identifiquei a data de vencimento.");
        }
        if (dados.dataVencimento().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                "A data de vencimento (" + dados.dataVencimento().format(FMT_BR)
                    + ") já passou. Não é possível agendar.");
        }
    }

    private DadosPagamento dadosFromContexto(Map<String, Object> ctx) {
        String tipo = String.valueOf(ctx.getOrDefault("tipo", "BOLETO"));
        String beneficiario = String.valueOf(ctx.getOrDefault("beneficiario", "Beneficiário"));
        BigDecimal valor = ctx.get("valor") instanceof Number n
            ? BigDecimal.valueOf(n.doubleValue()).setScale(SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        LocalDate venc = LocalDate.parse(String.valueOf(ctx.get("dataVencimento")));
        String codigo = String.valueOf(ctx.getOrDefault("codigoBarrasOuPix", ""));
        return new DadosPagamento(tipo, beneficiario, valor, venc, codigo);
    }

    private Long resolverContaId(Map<String, Object> ctx) {
        Object id = ctx.get("contaBancariaId");
        if (id instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalArgumentException("Conta de débito não definida na sessão.");
    }

    private BigDecimal lerValor(JsonNode node) {
        if (node.has("valor") && !node.path("valor").isNull()) {
            return node.path("valor").decimalValue().setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (node.has("valorTotal") && !node.path("valorTotal").isNull()) {
            return node.path("valorTotal").decimalValue().setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (node.has("amount") && !node.path("amount").isNull()) {
            return node.path("amount").decimalValue().setScale(SCALE, RoundingMode.HALF_UP);
        }
        return null;
    }

    private LocalDate lerDataVencimento(JsonNode node) {
        String raw = node.path("dataVencimento").asText("").trim();
        if (raw.isBlank()) {
            raw = node.path("vencimento").asText("").trim();
        }
        if (raw.isBlank()) {
            return null;
        }
        try {
            if (raw.contains("/")) {
                return LocalDate.parse(raw, FMT_BR);
            }
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Remove quebras de linha e espaços do payload bruto. */
    public static String higienizarCodigo(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", "").trim();
    }

    private AgendamentoPagamentoDTO toDto(AgendamentoPagamento a) {
        return AgendamentoPagamentoDTO.builder()
            .id(a.getId())
            .contaDebitoId(a.getContaDebito() != null ? a.getContaDebito().getId() : null)
            .contaDebitoNome(a.getContaDebito() != null ? a.getContaDebito().getNome() : null)
            .beneficiario(a.getBeneficiario())
            .valor(a.getValor())
            .dataVencimento(a.getDataVencimento())
            .codigoBarrasOuPix(a.getCodigoBarrasOuPix())
            .status(a.getStatus() != null ? a.getStatus().name() : null)
            .dataCriacao(a.getDataCriacao())
            .dataProcessamento(a.getDataProcessamento())
            .mensagemErro(a.getMensagemErro())
            .build();
    }
}
