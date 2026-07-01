package com.consumoesperto.service;

import com.consumoesperto.dto.DivergenciaSaldoDTO;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.repository.ContaBancariaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detecta divergência entre saldo persistido e soma dos movimentos confirmados por conta.
 * Correção automática só quando {@code consumoesperto.saldo.integridade.auto-corrigir=true}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaldoIntegridadeService {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.02");

    private final ContaBancariaRepository contaBancariaRepository;
    private final SaldoService saldoService;

    @Value("${consumoesperto.saldo.integridade.auto-corrigir:false}")
    private boolean autoCorrigir;

    @Transactional(readOnly = true)
    public List<DivergenciaSaldoDTO> auditarUsuario(Long usuarioId) {
        List<DivergenciaSaldoDTO> out = new ArrayList<>();
        for (ContaBancaria conta : contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId)) {
            auditarConta(conta).ifPresent(out::add);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<DivergenciaSaldoDTO> auditarConta(ContaBancaria conta) {
        if (conta == null || conta.getId() == null || conta.getUsuario() == null) {
            return Optional.empty();
        }
        Long usuarioId = conta.getUsuario().getId();
        BigDecimal persistido = nz(conta.getSaldoAtual());
        BigDecimal calculado = saldoService.calcularSaldoEsperadoPorMovimentos(conta.getId(), usuarioId);
        BigDecimal delta = persistido.subtract(calculado).setScale(2, RoundingMode.HALF_UP);
        if (delta.abs().compareTo(TOLERANCIA) <= 0) {
            return Optional.empty();
        }
        return Optional.of(DivergenciaSaldoDTO.builder()
            .contaId(conta.getId())
            .usuarioId(usuarioId)
            .nomeConta(conta.getNome())
            .saldoPersistido(persistido)
            .saldoCalculado(calculado)
            .delta(delta)
            .build());
    }

    @Transactional
    public List<DivergenciaSaldoDTO> auditarECorrigirSeHabilitado(Long usuarioId) {
        List<DivergenciaSaldoDTO> divergencias = auditarUsuario(usuarioId);
        if (autoCorrigir) {
            for (DivergenciaSaldoDTO d : divergencias) {
                saldoService.reconciliarSaldo(d.getContaId(), d.getUsuarioId());
                log.warn("[INTEGRIDADE-SALDO] Auto-corrigido contaId={} userId={} delta={}",
                    d.getContaId(), d.getUsuarioId(), d.getDelta());
            }
        } else if (!divergencias.isEmpty()) {
            log.warn("[INTEGRIDADE-SALDO] {} divergência(s) userId={} — auto-corrigir desligado",
                divergencias.size(), usuarioId);
        }
        return divergencias;
    }

    @Scheduled(cron = "${consumoesperto.saldo.integridade.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void jobDiarioDeteccao() {
        int divergentes = 0;
        for (ContaBancaria conta : contaBancariaRepository.findByAtivaTrue()) {
            if (auditarConta(conta).isPresent()) {
                divergentes++;
            }
        }
        if (divergentes > 0) {
            log.warn("[INTEGRIDADE-SALDO] Job diário: {} conta(s) com divergência detectada", divergentes);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
