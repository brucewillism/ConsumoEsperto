package com.consumoesperto.service;

import com.consumoesperto.dto.RecalculoProjecaoSazonalDTO;
import com.consumoesperto.dto.RendaDTO;
import com.consumoesperto.dto.RendaProcessamentoDTO;
import com.consumoesperto.dto.RendaRequestDTO;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Renda;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.RendaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RendaService {

    private final RendaRepository rendaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContaBancariaService contaBancariaService;
    private final AmortizacaoSazonalService amortizacaoSazonalService;
    private final SentinelaBufferSazonalService sentinelaBufferSazonalService;
    private final SaldoService saldoService;

    @Transactional(readOnly = true)
    public List<RendaDTO> listar(Long usuarioId, boolean apenasAtivas) {
        List<Renda> rendas = apenasAtivas
            ? rendaRepository.findByUsuarioIdAndAtivaTrueOrderByDescricaoAsc(usuarioId)
            : rendaRepository.findByUsuarioIdOrderByDescricaoAsc(usuarioId);
        return rendas.stream().map(this::toDto).toList();
    }

    @Transactional
    public RendaProcessamentoDTO salvar(Long usuarioId, RendaRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Corpo da requisição vazio.");
        }

        ContaBancaria contaDestino = contaBancariaService.buscarEntidade(
            request.getContaDestinoId(), usuarioId);

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Utilizador não encontrado"));

        Renda renda = new Renda();
        renda.setUsuario(usuario);
        renda.setDescricao(request.getDescricao().trim());
        renda.setValor(escala(request.getValor()));
        renda.setDiaPagamento(Math.max(1, Math.min(31, request.getDiaPagamento())));
        renda.setContaDestino(contaDestino);
        renda.setAtiva(true);
        renda = rendaRepository.save(renda);

        boolean creditoAplicado = false;
        if (!Boolean.FALSE.equals(request.getCreditarAgora())) {
            creditoAplicado = creditarEntradaRenda(renda);
            renda.setContaDestino(contaBancariaService.buscarEntidade(contaDestino.getId(), usuarioId));
        }

        RecalculoProjecaoSazonalDTO projecao = recalcularProjecoesSazonais(usuarioId, contaDestino.getId());

        log.info("[RENDA] Cadastrada id={} userId={} conta={} credito={}",
            renda.getId(), usuarioId, contaDestino.getId(), creditoAplicado);

        return RendaProcessamentoDTO.builder()
            .renda(toDto(renda))
            .creditoAplicado(creditoAplicado)
            .projecaoSazonal(projecao)
            .build();
    }

    /**
     * Credita o valor da renda na conta de destino e marca o mês civil para evitar duplicidade.
     */
    @Transactional
    public boolean creditarEntradaRenda(Renda renda) {
        if (renda == null || renda.getContaDestino() == null || renda.getUsuario() == null) {
            return false;
        }
        Long usuarioId = renda.getUsuario().getId();
        Long contaId = renda.getContaDestino().getId();
        int ym = YearMonth.now().getYear() * 100 + YearMonth.now().getMonthValue();

        if (renda.getUltimoMesCredito() != null && renda.getUltimoMesCredito() == ym) {
            log.debug("[RENDA] Crédito já aplicado no mês {} rendaId={}", ym, renda.getId());
            return false;
        }

        contaBancariaService.creditarValor(contaId, usuarioId, renda.getValor());
        renda.setUltimoMesCredito(ym);
        rendaRepository.save(renda);
        return true;
    }

    /**
     * Simula/processa entrada de renda sem persistir novo cadastro — credita na conta e recalcula projeções.
     */
    @Transactional
    public RecalculoProjecaoSazonalDTO simularEntradaRenda(Long usuarioId, RendaRequestDTO request) {
        ContaBancaria conta = contaBancariaService.buscarEntidade(request.getContaDestinoId(), usuarioId);
        contaBancariaService.creditarValor(conta.getId(), usuarioId, request.getValor());
        log.info("[RENDA] Simulação de entrada userId={} conta={} valor={}",
            usuarioId, conta.getId(), request.getValor());
        return recalcularProjecoesSazonais(usuarioId, conta.getId());
    }

    /**
     * Dispara recálculo imediato das projeções sazonais após alteração de saldo em conta.
     */
    @Transactional(readOnly = true)
    public RecalculoProjecaoSazonalDTO recalcularProjecoesSazonais(Long usuarioId, Long contaDestinoId) {
        BigDecimal patrimonio = saldoService.patrimonioLiquido(usuarioId);
        var colchao = sentinelaBufferSazonalService.recalcularColchao(usuarioId, contaDestinoId);
        var oportunidades = amortizacaoSazonalService.recalcularOportunidades(usuarioId, contaDestinoId);

        log.debug("[RENDA] Recálculo sazonal userId={} patrimonio={} oportunidades={}",
            usuarioId, patrimonio, oportunidades.size());

        return RecalculoProjecaoSazonalDTO.builder()
            .patrimonioLiquido(patrimonio)
            .colchaoSazonal(colchao)
            .oportunidadesAmortizacao(oportunidades)
            .build();
    }

    private RendaDTO toDto(Renda renda) {
        ContaBancaria conta = renda.getContaDestino();
        return RendaDTO.builder()
            .id(renda.getId())
            .descricao(renda.getDescricao())
            .valor(renda.getValor())
            .diaPagamento(renda.getDiaPagamento())
            .contaDestinoId(conta != null ? conta.getId() : null)
            .contaDestinoNome(conta != null ? conta.getNome() : null)
            .saldoContaDestino(conta != null ? conta.getSaldoAtual() : null)
            .ativa(renda.isAtiva())
            .ultimoMesCredito(renda.getUltimoMesCredito())
            .dataCriacao(renda.getDataCriacao())
            .dataAtualizacao(renda.getDataAtualizacao())
            .build();
    }

    private static BigDecimal escala(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
