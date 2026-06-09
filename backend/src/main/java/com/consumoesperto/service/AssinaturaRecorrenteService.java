package com.consumoesperto.service;

import com.consumoesperto.dto.AssinaturaRecorrenteDTO;
import com.consumoesperto.dto.AssinaturaRecorrenteRequest;
import com.consumoesperto.model.AssinaturaRecorrente;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AssinaturaRecorrenteRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gestor proativo de assinaturas e recorrências — detecção, cadastro, alertas 3 dias antes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssinaturaRecorrenteService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final int DIAS_ANTECEDENCIA_ALERTA = 3;
    private static final int SESSAO_TTL_MIN = 30;
    private static final int MESES_ANALISE = 6;

    private static final ThreadLocal<Boolean> RESPOSTA_WHATSAPP_SINCRONA = new ThreadLocal<>();

    private final AssinaturaRecorrenteRepository assinaturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContaBancariaService contaBancariaService;
    private final UsuarioSessaoContextoService sessaoContextoService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    public record DeteccaoRecorrencia(
        String nomeExibicao,
        BigDecimal valorMedio,
        int diaVencimento,
        Long contaBancariaId
    ) {}

    /** Evita notificação duplicada quando o WhatsApp já anexa a sugestão na mesma resposta. */
    public void executarComRespostaWhatsappSincrona(Runnable action) {
        RESPOSTA_WHATSAPP_SINCRONA.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            RESPOSTA_WHATSAPP_SINCRONA.remove();
        }
    }

    public boolean isRespostaWhatsappSincrona() {
        return Boolean.TRUE.equals(RESPOSTA_WHATSAPP_SINCRONA.get());
    }

    @Transactional(readOnly = true)
    public List<AssinaturaRecorrenteDTO> listar(Long usuarioId) {
        return assinaturaRepository.findByUsuarioIdOrderByNomeAscIdAsc(usuarioId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public AssinaturaRecorrenteDTO criar(Long usuarioId, AssinaturaRecorrenteRequest req) {
        if (encontrarSimilar(usuarioId, req.getNome(), null).isPresent()) {
            throw new IllegalArgumentException("Já existe assinatura com nome semelhante.");
        }
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        AssinaturaRecorrente e = new AssinaturaRecorrente();
        e.setUsuario(u);
        aplicar(e, req, usuarioId);
        return toDto(assinaturaRepository.save(e));
    }

    @Transactional
    public AssinaturaRecorrenteDTO atualizar(Long usuarioId, Long id, AssinaturaRecorrenteRequest req) {
        if (encontrarSimilar(usuarioId, req.getNome(), id).isPresent()) {
            throw new IllegalArgumentException("Já existe assinatura com nome semelhante.");
        }
        AssinaturaRecorrente e = buscarEntidade(id, usuarioId);
        aplicar(e, req, usuarioId);
        return toDto(assinaturaRepository.save(e));
    }

    @Transactional
    public AssinaturaRecorrenteDTO alternarAtivo(Long usuarioId, Long id, boolean ativo) {
        AssinaturaRecorrente e = buscarEntidade(id, usuarioId);
        e.setAtivo(ativo);
        return toDto(assinaturaRepository.save(e));
    }

    @Transactional
    public AssinaturaRecorrenteDTO alternarPorNome(Long usuarioId, String nome, boolean ativo) {
        AssinaturaRecorrente e = encontrarSimilar(usuarioId, nome, null)
            .orElseThrow(() -> new IllegalArgumentException(
                "Não encontrei assinatura cadastrada com o nome «" + nome + "»."));
        e.setAtivo(ativo);
        return toDto(assinaturaRepository.save(e));
    }

    @Transactional
    public void excluir(Long usuarioId, Long id) {
        AssinaturaRecorrente e = buscarEntidade(id, usuarioId);
        assinaturaRepository.delete(e);
    }

    /** Avalia padrão recorrente após despesa confirmada e propõe cadastro (sessão + WhatsApp). */
    @Transactional
    public void avaliarPropostaAposDespesa(Transacao transacao) {
        if (transacao == null || transacao.getUsuario() == null
            || transacao.getTipoTransacao() != Transacao.TipoTransacao.DESPESA
            || transacao.getStatusConferencia() != Transacao.StatusConferencia.CONFIRMADA) {
            return;
        }
        Long usuarioId = transacao.getUsuario().getId();
        if (temPropostaPendente(usuarioId) || jaTemAssinaturaSimilar(usuarioId, transacao.getDescricao())) {
            return;
        }
        detectarPadrao(usuarioId, transacao.getDescricao(), transacao.getValor())
            .ifPresent(d -> {
                salvarPropostaSessao(usuarioId, d);
                if (!isRespostaWhatsappSincrona()) {
                    notificarPropostaWhatsapp(usuarioId, d);
                }
            });
    }

    public boolean temPropostaPendente(Long usuarioId) {
        return sessaoContextoService.buscarAtiva(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_ASSINATURA_CONFIRMACAO
        ).isPresent();
    }

    public String mensagemPropostaAtiva(Long usuarioId) {
        return sessaoContextoService.buscarAtiva(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_ASSINATURA_CONFIRMACAO
        ).map(this::mensagemSugestaoFromContexto)
            .orElse("");
    }

    @Transactional
    public String confirmarProposta(Long usuarioId) {
        Map<String, Object> ctx = sessaoContextoService.buscarAtiva(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_ASSINATURA_CONFIRMACAO
        ).orElseThrow(() -> new IllegalArgumentException("Não há assinatura pendente de confirmação."));

        sessaoContextoService.remover(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_ASSINATURA_CONFIRMACAO
        );

        DeteccaoRecorrencia d = dadosFromContexto(ctx);
        if (jaTemAssinaturaSimilar(usuarioId, d.nomeExibicao())) {
            throw new IllegalArgumentException("Você já tem essa assinatura cadastrada.");
        }

        AssinaturaRecorrenteRequest req = new AssinaturaRecorrenteRequest();
        req.setNome(d.nomeExibicao());
        req.setValor(d.valorMedio());
        req.setDiaVencimento(d.diaVencimento());
        req.setContaDebitoPadraoId(d.contaBancariaId());
        req.setAtivo(true);
        AssinaturaRecorrenteDTO salva = criar(usuarioId, req);

        return "Assinatura *" + salva.getNome() + "* salva! Vou monitorar os *"
            + BRL.format(salva.getValor()) + "* todo dia *" + salva.getDiaVencimento()
            + "* e avisar 3 dias antes do vencimento. Gerencie em *Assinaturas* no app.";
    }

    @Transactional
    public void cancelarProposta(Long usuarioId) {
        sessaoContextoService.remover(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_ASSINATURA_CONFIRMACAO
        );
    }

    @Transactional(readOnly = true)
    public List<AssinaturaRecorrenteDTO> listarVencendoEmDias(Long usuarioId, int dias) {
        LocalDate hoje = LocalDate.now();
        return assinaturaRepository.findByUsuarioIdOrderByNomeAscIdAsc(usuarioId).stream()
            .filter(AssinaturaRecorrente::isAtivo)
            .filter(a -> venceEmDias(a, hoje, dias))
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal totalAssinaturasAtivas(Long usuarioId) {
        return assinaturaRepository.findByUsuarioIdOrderByNomeAscIdAsc(usuarioId).stream()
            .filter(AssinaturaRecorrente::isAtivo)
            .map(AssinaturaRecorrente::getValor)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /** Job diário: alerta assinaturas com vencimento efetivo em hoje + 3 dias. */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void alertarVencimentosProximos() {
        LocalDate hoje = LocalDate.now();
        List<AssinaturaRecorrente> ativas = assinaturaRepository.findByAtivoTrue();
        if (ativas.isEmpty()) {
            return;
        }
        log.info("[ASSINATURA] Verificando {} assinatura(s) ativa(s) — alvo vencimento {}", ativas.size(), hoje.plusDays(DIAS_ANTECEDENCIA_ALERTA));
        for (AssinaturaRecorrente a : ativas) {
            if (!venceEmDias(a, hoje, DIAS_ANTECEDENCIA_ALERTA)) {
                continue;
            }
            Usuario u = a.getUsuario();
            if (u == null || u.getWhatsappNumero() == null || u.getWhatsappNumero().isBlank()) {
                continue;
            }
            try {
                String msg = montarAlertaVencimento(u.getId(), a);
                whatsAppNotificationService.enviarParaUsuario(u.getId(), msg);
            } catch (Exception e) {
                log.warn("[ASSINATURA] Falha alerta id={} userId={}: {}", a.getId(), u.getId(), e.getMessage());
            }
        }
    }

    public Optional<DeteccaoRecorrencia> detectarPadrao(Long usuarioId, String descricao, BigDecimal valorAtual) {
        if (descricao == null || descricao.isBlank() || valorAtual == null) {
            return Optional.empty();
        }
        LocalDateTime inicio = LocalDate.now().minusMonths(MESES_ANALISE).atStartOfDay();
        List<Transacao> linhas = transacaoRepository.findDespesasConfirmadasDesde(usuarioId, inicio);
        String chave = chaveAgrupamento(descricao);
        List<Transacao> grupo = linhas.stream()
            .filter(t -> chaveAgrupamento(t.getDescricao()).equals(chave))
            .toList();
        if (grupo.size() < 2) {
            return Optional.empty();
        }
        Set<YearMonth> meses = grupo.stream()
            .map(t -> YearMonth.from(t.getDataTransacao().toLocalDate()))
            .collect(Collectors.toSet());
        if (meses.size() < 2) {
            return Optional.empty();
        }
        List<BigDecimal> valores = grupo.stream().map(Transacao::getValor).filter(Objects::nonNull).toList();
        if (valores.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal soma = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal media = soma.divide(BigDecimal.valueOf(valores.size()), 2, RoundingMode.HALF_UP);
        if (media.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal min = valores.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal max = valores.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal ratioMin = min.divide(media, 4, RoundingMode.HALF_UP);
        BigDecimal ratioMax = max.divide(media, 4, RoundingMode.HALF_UP);
        if (ratioMin.compareTo(new BigDecimal("0.78")) < 0 || ratioMax.compareTo(new BigDecimal("1.22")) > 0) {
            return Optional.empty();
        }
        int diaMedio = Math.max(1, Math.min(31, (int) Math.round(grupo.stream()
            .mapToInt(t -> t.getDataTransacao().getDayOfMonth())
            .average()
            .orElse(LocalDate.now().getDayOfMonth()))));
        String nome = rotuloAmigavel(descricao);
        Long contaId = grupo.stream()
            .sorted(Comparator.comparing(Transacao::getDataTransacao).reversed())
            .map(Transacao::getContaBancaria)
            .filter(Objects::nonNull)
            .map(ContaBancaria::getId)
            .findFirst()
            .orElse(null);
        if (contaId == null) {
            ContaBancaria padrao = contaBancariaService.resolverContaParaTransacao(usuarioId, null);
            contaId = padrao != null ? padrao.getId() : null;
        }
        return Optional.of(new DeteccaoRecorrencia(nome, media, diaMedio, contaId));
    }

    /**
     * Verifica se o vencimento efetivo da assinatura coincide com {@code hoje + diasAntecedencia},
     * respeitando meses curtos (ex.: dia 31 → 30 em abril, 28 em fevereiro).
     */
    public static boolean venceEmDias(AssinaturaRecorrente a, LocalDate hoje, int diasAntecedencia) {
        if (a == null || a.getDiaVencimento() == null) {
            return false;
        }
        LocalDate alvo = hoje.plusDays(diasAntecedencia);
        YearMonth ym = YearMonth.from(alvo);
        int efetivo = diaEfetivoNoMes(a.getDiaVencimento(), ym.lengthOfMonth());
        return efetivo == alvo.getDayOfMonth();
    }

    public static int diaEfetivoNoMes(int diaVencimento, int ultimoDiaMes) {
        return Math.min(Math.max(1, diaVencimento), ultimoDiaMes);
    }

    private String montarAlertaVencimento(Long usuarioId, AssinaturaRecorrente a) {
        ContaBancaria conta = resolverContaDebito(usuarioId, a);
        String nome = a.getNome();
        BigDecimal valor = a.getValor() != null ? a.getValor() : BigDecimal.ZERO;
        BigDecimal saldo = conta.getSaldoAtual() != null ? conta.getSaldoAtual() : BigDecimal.ZERO;

        if (!conta.temSaldoSuficiente(valor)) {
            return "⚠️ *URGENTE*, chefe: a assinatura da *" + nome + "* (*" + BRL.format(valor)
                + "*) vence em *3 dias*. Saldo disponível na conta *" + conta.getNome() + "*: *"
                + BRL.format(conta.getSaldoDisponivel()) + "*. Não cobre o débito nem com cheque especial. "
                + "Faça um Pix para lá ou pause a assinatura no app.";
        }
        if (saldo.compareTo(valor) >= 0) {
            return "Chefe, lembrete: a assinatura da *" + nome + "* (*" + BRL.format(valor)
                + "*) vence em *3 dias*. A conta *" + conta.getNome() + "* tem saldo suficiente (*"
                + BRL.format(saldo) + "*).";
        }
        BigDecimal usoCheque = valor.subtract(saldo.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
        return "Atenção, chefe: a assinatura da sua *" + nome + "* (*" + BRL.format(valor)
            + "*) vence em *3 dias*. O seu saldo real é de *" + BRL.format(saldo)
            + "*. Esse débito vai ativar o seu *cheque especial* em *" + BRL.format(usoCheque)
            + "* na conta *" + conta.getNome() + "*. Quer que eu te lembre de fazer um Pix para lá?";
    }

    private ContaBancaria resolverContaDebito(Long usuarioId, AssinaturaRecorrente a) {
        if (a.getContaDebitoPadrao() != null && a.getContaDebitoPadrao().getId() != null) {
            return contaBancariaService.buscarEntidade(a.getContaDebitoPadrao().getId(), usuarioId);
        }
        ContaBancaria padrao = contaBancariaService.resolverContaParaTransacao(usuarioId, null);
        if (padrao == null) {
            throw new IllegalStateException("Nenhuma conta bancária para validar assinatura.");
        }
        return padrao;
    }

    private void salvarPropostaSessao(Long usuarioId, DeteccaoRecorrencia d) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("nome", d.nomeExibicao());
        ctx.put("valor", d.valorMedio());
        ctx.put("diaVencimento", d.diaVencimento());
        ctx.put("contaBancariaId", d.contaBancariaId());
        sessaoContextoService.salvar(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_ASSINATURA_CONFIRMACAO,
            ctx,
            SESSAO_TTL_MIN
        );
    }

    private void notificarPropostaWhatsapp(Long usuarioId, DeteccaoRecorrencia d) {
        try {
            Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
            if (u == null || u.getWhatsappNumero() == null || u.getWhatsappNumero().isBlank()) {
                return;
            }
            whatsAppNotificationService.enviarParaUsuario(usuarioId, mensagemSugestao(d));
        } catch (Exception e) {
            log.debug("Falha ao notificar proposta assinatura userId={}: {}", usuarioId, e.getMessage());
        }
    }

    private String mensagemSugestao(DeteccaoRecorrencia d) {
        return "Chefe, notei que você paga *" + d.nomeExibicao() + "* todo mês (cerca de *"
            + BRL.format(d.valorMedio()) + "*). Quer que eu salve isso como uma *Assinatura ativa* "
            + "para eu passar a monitorar para você? Responda *sim* ou *não*.";
    }

    private String mensagemSugestaoFromContexto(Map<String, Object> ctx) {
        String nome = String.valueOf(ctx.getOrDefault("nome", "assinatura"));
        BigDecimal valor = ctx.get("valor") instanceof Number n
            ? BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        return "\n\n" + mensagemSugestao(new DeteccaoRecorrencia(nome, valor, 1, null));
    }

    private DeteccaoRecorrencia dadosFromContexto(Map<String, Object> ctx) {
        String nome = String.valueOf(ctx.getOrDefault("nome", "")).trim();
        BigDecimal valor = ctx.get("valor") instanceof Number n
            ? BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        int dia = ctx.get("diaVencimento") instanceof Number n ? n.intValue() : LocalDate.now().getDayOfMonth();
        Long contaId = ctx.get("contaBancariaId") instanceof Number n ? n.longValue() : null;
        return new DeteccaoRecorrencia(nome, valor, dia, contaId);
    }

    private boolean jaTemAssinaturaSimilar(Long usuarioId, String descricao) {
        return encontrarSimilar(usuarioId, descricao, null).isPresent();
    }

    private Optional<AssinaturaRecorrente> encontrarSimilar(Long usuarioId, String descricao, Long excludeId) {
        String n = normalizeDesc(descricao);
        if (n.length() < 2) {
            return Optional.empty();
        }
        for (AssinaturaRecorrente a : assinaturaRepository.findByUsuarioIdOrderByNomeAscIdAsc(usuarioId)) {
            if (excludeId != null && excludeId.equals(a.getId())) {
                continue;
            }
            String o = normalizeDesc(a.getNome());
            if (o.length() < 2) {
                continue;
            }
            if (o.equals(n) || o.contains(n) || n.contains(o)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    private AssinaturaRecorrente buscarEntidade(Long id, Long usuarioId) {
        AssinaturaRecorrente e = assinaturaRepository.findByIdAndUsuarioId(id, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Assinatura não encontrada."));
        return e;
    }

    private void aplicar(AssinaturaRecorrente e, AssinaturaRecorrenteRequest req, Long usuarioId) {
        e.setNome(req.getNome().trim());
        e.setValor(req.getValor().setScale(2, RoundingMode.HALF_UP));
        e.setDiaVencimento(req.getDiaVencimento());
        if (req.getAtivo() != null) {
            e.setAtivo(req.getAtivo());
        }
        if (req.getContaDebitoPadraoId() != null) {
            e.setContaDebitoPadrao(contaBancariaService.buscarEntidade(req.getContaDebitoPadraoId(), usuarioId));
        }
    }

    private AssinaturaRecorrenteDTO toDto(AssinaturaRecorrente a) {
        ContaBancaria conta = a.getContaDebitoPadrao();
        return AssinaturaRecorrenteDTO.builder()
            .id(a.getId())
            .nome(a.getNome())
            .valor(a.getValor())
            .diaVencimento(a.getDiaVencimento())
            .contaDebitoPadraoId(conta != null ? conta.getId() : null)
            .contaDebitoPadraoNome(conta != null ? conta.getNome() : null)
            .ativo(a.isAtivo())
            .build();
    }

    private static String chaveAgrupamento(String descricao) {
        String desc = normalizar(descricao)
            .replaceAll("\\b(parcela|pagamento|compra|debito|credito|cartao|pix)\\b", "")
            .replaceAll("\\s+", " ")
            .trim();
        if (desc.length() > 28) {
            desc = desc.substring(0, 28);
        }
        return desc;
    }

    private static String rotuloAmigavel(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return "Assinatura";
        }
        String t = descricao.trim();
        if (t.length() > 48) {
            t = t.substring(0, 45) + "...";
        }
        return t;
    }

    private static String normalizar(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ");
    }

    private static String normalizeDesc(String raw) {
        return normalizar(raw).replaceAll("\\s+", " ").trim();
    }
}
