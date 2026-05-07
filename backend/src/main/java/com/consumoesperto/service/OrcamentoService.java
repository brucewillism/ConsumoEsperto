package com.consumoesperto.service;

import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.dto.OrcamentoRequest;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.GrupoFamiliar;
import com.consumoesperto.model.Orcamento;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.OrcamentoRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrcamentoService {

    private final OrcamentoRepository orcamentoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransacaoRepository transacaoRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final ScoreService scoreService;
    private final GrupoFamiliarService grupoFamiliarService;
    private final JarvisProtocolService jarvisProtocolService;

    @Transactional(readOnly = true)
    public List<OrcamentoDTO> listar(Long usuarioId, Integer mes, Integer ano) {
        YearMonth ym = resolverMesAno(mes, ano);
        return orcamentoRepository.findByUsuarioIdAndMesAndAno(usuarioId, ym.getMonthValue(), ym.getYear())
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public OrcamentoDTO salvar(Long usuarioId, OrcamentoRequest request) {
        YearMonth ym = resolverMesAno(request.getMes(), request.getAno());
        Categoria categoria = categoriaRepository.findById(request.getCategoriaId())
            .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));
        if (categoria.getUsuario() == null || !categoria.getUsuario().getId().equals(usuarioId)) {
            throw new IllegalArgumentException("Categoria não pertence ao usuário");
        }
        Orcamento orcamento = orcamentoRepository
            .findByUsuarioIdAndCategoriaIdAndMesAndAno(usuarioId, categoria.getId(), ym.getMonthValue(), ym.getYear())
            .orElseGet(Orcamento::new);
        if (orcamento.getId() == null) {
            Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
            orcamento.setUsuario(usuario);
            orcamento.setCategoria(categoria);
            orcamento.setMes(ym.getMonthValue());
            orcamento.setAno(ym.getYear());
        }
        boolean compartilhado = Boolean.TRUE.equals(request.getCompartilhado());
        orcamento.setCompartilhado(compartilhado);
        if (compartilhado) {
            GrupoFamiliar grupo = grupoFamiliarService.grupoAceitoDoUsuario(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Para compartilhar orçamento, crie ou aceite um grupo familiar."));
            orcamento.setGrupoFamiliar(grupo);
        } else {
            orcamento.setGrupoFamiliar(null);
        }
        orcamento.setValorLimite(request.getValorLimite().setScale(2, RoundingMode.HALF_UP));
        return toDto(orcamentoRepository.save(orcamento));
    }

    @Transactional(readOnly = true)
    public List<OrcamentoDTO> listarCompartilhados(Long usuarioId, Integer mes, Integer ano) {
        YearMonth ym = resolverMesAno(mes, ano);
        return grupoFamiliarService.grupoAceitoDoUsuario(usuarioId)
            .map(g -> orcamentoRepository.findCompartilhadosByGrupoIdAndMesAndAno(g.getId(), ym.getMonthValue(), ym.getYear())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList()))
            .orElse(List.of());
    }

    @Transactional
    public void excluir(Long usuarioId, Long id) {
        Orcamento o = orcamentoRepository.findByIdAndUsuarioId(id, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Orçamento não encontrado"));
        orcamentoRepository.delete(o);
    }

    @Transactional(readOnly = true)
    public Optional<AlertaOrcamento> verificarAposDespesa(Transacao transacao) {
        if (!isDespesaConfirmadaComCategoria(transacao)) {
            return Optional.empty();
        }
        Long usuarioId = transacao.getUsuario().getId();
        Long categoriaId = transacao.getCategoria().getId();
        YearMonth ym = YearMonth.from(transacao.getDataTransacao());
        Optional<Orcamento> opt = orcamentoRepository.findByUsuarioIdAndCategoriaIdAndMesAndAno(
            usuarioId, categoriaId, ym.getMonthValue(), ym.getYear());
        if (opt.isEmpty()) {
            opt = grupoFamiliarService.grupoAceitoDoUsuario(usuarioId)
                .stream()
                .flatMap(g -> orcamentoRepository.findCompartilhadosByGrupoIdAndMesAndAno(
                    g.getId(), ym.getMonthValue(), ym.getYear()).stream())
                .filter(o -> normalizar(o.getCategoria().getNome()).equals(normalizar(transacao.getCategoria().getNome())))
                .findFirst();
        }
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Orcamento o = opt.get();
        BigDecimal gastoAtual = gastoOrcamento(o, ym);
        BigDecimal valorTransacao = transacao.getValor() != null ? transacao.getValor() : BigDecimal.ZERO;
        BigDecimal gastoAnterior = gastoAtual.subtract(valorTransacao).max(BigDecimal.ZERO);
        BigDecimal limite = o.getValorLimite();
        if (limite == null || limite.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal pctAnterior = percentual(gastoAnterior, limite);
        BigDecimal pctAtual = percentual(gastoAtual, limite);
        int marco = 0;
        if (pctAnterior.compareTo(BigDecimal.valueOf(100)) < 0 && pctAtual.compareTo(BigDecimal.valueOf(100)) >= 0) {
            marco = 100;
        } else if (pctAnterior.compareTo(BigDecimal.valueOf(80)) < 0 && pctAtual.compareTo(BigDecimal.valueOf(80)) >= 0) {
            marco = 80;
        }
        if (marco == 0) {
            return Optional.empty();
        }
        AlertaOrcamento alerta = new AlertaOrcamento(o, gastoAtual, pctAtual, marco);
        String msgJarvis = jarvisProtocolService.formatOrcamentoAlert(o, marco, gastoAtual, pctAtual);
        if (o.isCompartilhado() && o.getGrupoFamiliar() != null) {
            for (Usuario membro : grupoFamiliarService.membrosAceitos(o.getGrupoFamiliar().getId())) {
                whatsAppNotificationService.enviarParaUsuario(membro.getId(), msgJarvis);
            }
        } else {
            whatsAppNotificationService.enviarParaUsuario(usuarioId, msgJarvis);
        }
        if (marco >= 100) {
            scoreService.registrarEvento(usuarioId, ScoreService.EventoScore.ORCAMENTO_ESTOURADO,
                "Categoria " + o.getCategoria().getNome() + " ultrapassou o orçamento mensal");
        }
        return Optional.of(alerta);
    }

    public OrcamentoDTO toDto(Orcamento o) {
        OrcamentoDTO dto = new OrcamentoDTO();
        dto.setId(o.getId());
        dto.setCategoriaId(o.getCategoria().getId());
        dto.setCategoriaNome(o.getCategoria().getNome());
        dto.setValorLimite(nz(o.getValorLimite()));
        dto.setMes(o.getMes());
        dto.setAno(o.getAno());
        YearMonth ym = YearMonth.of(o.getAno(), o.getMes());
        BigDecimal gasto = gastoOrcamento(o, ym);
        dto.setValorGasto(gasto);
        BigDecimal pct = percentual(gasto, dto.getValorLimite());
        dto.setPercentualUso(pct);
        dto.setStatus(status(pct));
        dto.setCompartilhado(o.isCompartilhado());
        dto.setGrupoFamiliarId(o.getGrupoFamiliar() != null ? o.getGrupoFamiliar().getId() : null);
        dto.setMembrosContabilizados(o.isCompartilhado() && o.getGrupoFamiliar() != null
            ? grupoFamiliarService.membrosAceitos(o.getGrupoFamiliar().getId()).size()
            : 1);
        return dto;
    }

    private BigDecimal gastoOrcamento(Orcamento o, YearMonth ym) {
        if (o.isCompartilhado() && o.getGrupoFamiliar() != null) {
            List<Long> ids = grupoFamiliarService.membrosAceitos(o.getGrupoFamiliar().getId()).stream()
                .map(Usuario::getId)
                .collect(Collectors.toList());
            if (ids.isEmpty()) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            String categoriaNorm = normalizar(o.getCategoria().getNome());
            BigDecimal total = transacaoRepository.findDespesasConfirmadasPorUsuariosNoPeriodoComCategoria(
                    ids, ym.atDay(1).atStartOfDay(), ym.atEndOfMonth().atTime(23, 59, 59))
                .stream()
                .filter(t -> t.getCategoria() != null && normalizar(t.getCategoria().getNome()).equals(categoriaNorm))
                .map(t -> t.getValor() != null ? t.getValor() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return nz(total);
        }
        return gastoCategoria(o.getUsuario().getId(), o.getCategoria().getId(), ym);
    }

    private BigDecimal gastoCategoria(Long usuarioId, Long categoriaId, YearMonth ym) {
        BigDecimal v = transacaoRepository.sumDespesaConfirmadaPorCategoriaNoPeriodo(
            usuarioId, categoriaId, ym.atDay(1).atStartOfDay(), ym.atEndOfMonth().atTime(23, 59, 59));
        return nz(v);
    }

    private static boolean isDespesaConfirmadaComCategoria(Transacao t) {
        return t != null
            && t.getUsuario() != null
            && t.getUsuario().getId() != null
            && t.getCategoria() != null
            && t.getCategoria().getId() != null
            && t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA
            && t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA;
    }

    private static YearMonth resolverMesAno(Integer mes, Integer ano) {
        YearMonth now = YearMonth.now();
        int m = mes != null ? mes : now.getMonthValue();
        int a = ano != null ? ano : now.getYear();
        return YearMonth.of(a, m);
    }

    private static BigDecimal percentual(BigDecimal gasto, BigDecimal limite) {
        if (limite == null || limite.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return nz(gasto).multiply(BigDecimal.valueOf(100)).divide(limite, 2, RoundingMode.HALF_UP);
    }

    private static String status(BigDecimal pct) {
        if (pct.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "VERMELHO";
        }
        if (pct.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "AMARELO";
        }
        return "VERDE";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase()
            .replaceAll("[^a-z0-9]", "");
    }

    public record AlertaOrcamento(Orcamento orcamento, BigDecimal gastoAtual, BigDecimal percentualUso, int marco) {
    }
}
