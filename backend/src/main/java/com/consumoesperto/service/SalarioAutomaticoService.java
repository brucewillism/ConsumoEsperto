package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.RendaConfig;
import com.consumoesperto.model.TipoConfiguracaoRenda;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.RendaConfigRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.ApelidoNormalizador;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Lança receita confirmada de salário quando a opção automática está activa
 * e o dia de pagamento do mês já passou (catch-up se o backend não estava no ar no dia exacto).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalarioAutomaticoService {

    private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");
    /** Dia habitual de crédito de salário quando não configurado (ex.: dia 30). */
    public static final int DIA_PAGAMENTO_PADRAO = 30;

    private final RendaConfigRepository rendaConfigRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoService transacaoService;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SaldoService saldoService;
    private final ObjectProvider<ContrachequeImportService> contrachequeImportProvider;

    /**
     * @return {@code true} se criou a receita confirmada nesta invocação
     */
    @Transactional
    public boolean tentarLancarSalarioMesAtual(RendaConfig cfg) {
        if (cfg == null || !cfg.isReceitaAutomaticaAtiva()) {
            return false;
        }
        if (cfg.getTipoConfiguracaoRenda() == TipoConfiguracaoRenda.FLUXO_DIARIO) {
            return false;
        }
        LocalDate hoje = LocalDate.now(ZONA_BR);
        int ym = hoje.getYear() * 100 + hoje.getMonthValue();
        int diaPagamento = resolverDiaPagamento(cfg);
        if (diaPagamento > hoje.getDayOfMonth()) {
            return false;
        }
        if (cfg.getSalarioLiquido() == null || cfg.getSalarioLiquido().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        Usuario u = cfg.getUsuario();
        if (u == null) {
            return false;
        }
        Long uid = u.getId();
        Integer ultimo = cfg.getUltimoMesLancamentoAuto();
        List<Transacao> existentes = buscarReceitasSalarioMes(uid, hoje);
        if (!existentes.isEmpty()) {
            boolean efetivou = efetivarSaldoSalarioAgendado(cfg, uid, hoje);
            if (ultimo == null || ultimo != ym) {
                cfg.setUltimoMesLancamentoAuto(ym);
                rendaConfigRepository.save(cfg);
            }
            return efetivou;
        }
        if (ultimo != null && ultimo == ym) {
            cfg.setUltimoMesLancamentoAuto(null);
            rendaConfigRepository.save(cfg);
        }
        int diaEfetivo = Math.min(diaPagamento, hoje.lengthOfMonth());
        LocalDateTime dataLancamento = hoje.withDayOfMonth(diaEfetivo).atStartOfDay();

        TransacaoDTO dto = new TransacaoDTO();
        dto.setDescricao("Salário líquido (automático)");
        dto.setValor(cfg.getSalarioLiquido());
        dto.setTipoTransacao(TransacaoDTO.TipoTransacao.RECEITA);
        dto.setCategoriaId(resolveCategoriaSalario(uid));
        dto.setDataTransacao(dataLancamento);
        dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        resolverContaDestinoSalario(cfg, uid).ifPresent(dto::setContaBancariaId);
        transacaoService.criarTransacao(dto, uid);

        cfg.setUltimoMesLancamentoAuto(ym);
        rendaConfigRepository.save(cfg);
        try {
            contrachequeImportProvider.getObject().garantirEspelhoMesAtual(uid);
        } catch (Exception e) {
            log.warn("Espelho de contracheque após salário automático userId={}: {}", uid, e.getMessage());
        }
        log.info(
            "Salário automático lançado userId={} valor={} ym={} data={} contaId={}",
            uid,
            cfg.getSalarioLiquido(),
            ym,
            dataLancamento,
            dto.getContaBancariaId()
        );
        return true;
    }

    /** Dia de pagamento efectivo (1–31); omissão = {@link #DIA_PAGAMENTO_PADRAO}. */
    public static int resolverDiaPagamento(RendaConfig cfg) {
        Integer dia = cfg != null ? cfg.getDiaPagamento() : null;
        if (dia == null || dia < 1 || dia > 31) {
            return DIA_PAGAMENTO_PADRAO;
        }
        return dia;
    }

    /**
     * Conta que recebe o salário: configurada na renda, senão conta com «Itaú» no nome, senão padrão/única ativa.
     */
    public java.util.Optional<Long> resolverContaDestinoSalarioPorUsuario(Long usuarioId) {
        return rendaConfigRepository.findByUsuarioId(usuarioId)
            .flatMap(cfg -> resolverContaDestinoSalario(cfg, usuarioId));
    }

    public java.util.Optional<Long> resolverContaDestinoSalario(RendaConfig cfg, Long usuarioId) {
        if (cfg != null && cfg.getContaBancaria() != null) {
            return contaBancariaRepository.findByIdAndUsuarioId(cfg.getContaBancaria().getId(), usuarioId)
                .filter(ContaBancaria::isAtiva)
                .map(ContaBancaria::getId);
        }
        for (ContaBancaria conta : contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId)) {
            String nome = ApelidoNormalizador.normalizar(conta.getNome());
            if (nome.contains("itau")) {
                return java.util.Optional.of(conta.getId());
            }
        }
        return contaBancariaRepository.findFirstByUsuarioIdAndPadraoTrueAndAtivaTrue(usuarioId)
            .or(() -> contaBancariaRepository.findFirstByUsuarioIdAndAtivaTrueOrderByIdAsc(usuarioId))
            .map(ContaBancaria::getId);
    }

    @Transactional
    public boolean tentarLancarSalarioMesAtual(Long usuarioId) {
        return rendaConfigRepository.findByUsuarioId(usuarioId)
            .map(this::tentarLancarSalarioMesAtual)
            .orElse(false);
    }

    /**
     * Receita confirmada antes do dia de pagamento fica com data futura e não credita a conta na criação.
     * No dia do pagamento, reconcilia o saldo da conta destino em vez de duplicar o lançamento.
     */
    private boolean efetivarSaldoSalarioAgendado(RendaConfig cfg, Long usuarioId, LocalDate hoje) {
        List<Transacao> receitas = buscarReceitasSalarioMes(usuarioId, hoje);
        if (receitas.isEmpty()) {
            return false;
        }
        java.util.Optional<Long> contaId = resolverContaDestinoSalario(cfg, usuarioId);
        if (contaId.isEmpty()) {
            log.warn("Salário automático: receita do mês sem conta destino userId={}", usuarioId);
            return false;
        }
        for (Transacao tx : receitas) {
            if (tx.getContaBancaria() == null) {
                ContaBancaria conta = contaBancariaRepository.findById(contaId.get()).orElse(null);
                if (conta != null) {
                    tx.setContaBancaria(conta);
                    transacaoRepository.save(tx);
                }
            }
        }
        SaldoService.ResultadoReconciliacaoSaldo resultado = saldoService.reconciliarSaldo(contaId.get(), usuarioId);
        log.info(
            "Salário automático: saldo efetivado userId={} contaId={} saldo {} → {} ({} tx)",
            usuarioId,
            contaId.get(),
            resultado.saldoAnterior(),
            resultado.saldoCalculado(),
            resultado.transacoesConsideradas()
        );
        return true;
    }

    private List<Transacao> buscarReceitasSalarioMes(Long usuarioId, LocalDate ref) {
        YearMonth ym = YearMonth.from(ref);
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);
        return transacaoRepository.findByUsuarioIdAndDataTransacaoBetween(usuarioId, inicio, fim).stream()
            .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.RECEITA)
            .filter(t -> t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA)
            .filter(t -> t.getOrigemFiscal() == null)
            .filter(this::pareceReceitaSalario)
            .toList();
    }

    private boolean pareceReceitaSalario(Transacao t) {
        String desc = t.getDescricao() != null ? t.getDescricao().toLowerCase() : "";
        if (desc.contains("salário") || desc.contains("salario")) {
            return true;
        }
        if (t.getCategoria() != null && t.getCategoria().getNome() != null) {
            String cat = t.getCategoria().getNome().toLowerCase();
            return cat.contains("salário") || cat.contains("salario");
        }
        return false;
    }

    private Long resolveCategoriaSalario(Long usuarioId) {
        Categoria existente = categoriaRepository.findByUsuarioIdAndNome(usuarioId, "Salário");
        if (existente != null) {
            return existente.getId();
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        Categoria c = new Categoria();
        c.setUsuario(usuario);
        c.setNome("Salário");
        c.setDescricao("Receitas de salário/contracheque");
        c.setCor("#10b981");
        c.setIcone("money-bill-wave");
        return categoriaRepository.save(c).getId();
    }
}
