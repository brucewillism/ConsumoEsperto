package com.consumoesperto.service;

import com.consumoesperto.dto.SimulacaoImpactoDTO;
import com.consumoesperto.dto.SimulacaoImpactoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulacaoImpactoService {

    private final Map<Long, List<SimulacaoImpactoDTO>> cachePorUsuario = new ConcurrentHashMap<>();
    private final ScoreService scoreService;

    public List<SimulacaoImpactoDTO> listar(Long usuarioId) {
        return new ArrayList<>(cachePorUsuario.getOrDefault(usuarioId, List.of()));
    }

    public List<SimulacaoImpactoDTO> listarAtivas(Long usuarioId) {
        return listar(usuarioId).stream().filter(SimulacaoImpactoDTO::isAtiva).collect(Collectors.toList());
    }

    public SimulacaoImpactoDTO criar(Long usuarioId, SimulacaoImpactoRequest request) {
        SimulacaoImpactoDTO dto = new SimulacaoImpactoDTO();
        dto.setId(UUID.randomUUID().toString());
        dto.setDescricao(request.getDescricao().trim());
        dto.setValorMensalImpacto(nz(request.getValorMensalImpacto()));
        dto.setMesesImpacto(request.getMesesImpacto() != null && request.getMesesImpacto() > 0 ? request.getMesesImpacto() : 1);
        dto.setMetaDescricao(request.getMetaDescricao());
        dto.setIcone(request.getIcone() != null && !request.getIcone().isBlank() ? request.getIcone() : "bullseye");
        dto.setAtiva(true);
        dto.setCriadaEm(LocalDateTime.now());
        int perdaScore = scoreService.estimarPerdaSimulacao(dto.getValorMensalImpacto());
        dto.setImpactoScore(-perdaScore);
        String alertaScore = perdaScore >= 80
            ? " Essa compra reduzirá seu Score de Saúde Financeira em " + perdaScore + " pontos. Tem certeza?"
            : "";
        dto.setMensagem("Essa escolha pode atrasar a meta"
            + (dto.getMetaDescricao() != null && !dto.getMetaDescricao().isBlank() ? " \"" + dto.getMetaDescricao() + "\"" : "")
            + " em aproximadamente " + dto.getMesesImpacto() + " mês(es)." + alertaScore
            + " Deseja manter essa simulação ativa para monitoramento?");
        cachePorUsuario.computeIfAbsent(usuarioId, k -> new ArrayList<>()).add(dto);
        return dto;
    }

    public List<SimulacaoImpactoDTO> definirAtivas(Long usuarioId, boolean ativa) {
        List<SimulacaoImpactoDTO> lista = cachePorUsuario.getOrDefault(usuarioId, List.of());
        lista.forEach(s -> s.setAtiva(ativa));
        return listar(usuarioId);
    }

    public BigDecimal impactoMensalAtivo(Long usuarioId) {
        return listarAtivas(usuarioId).stream()
            .map(SimulacaoImpactoDTO::getValorMensalImpacto)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
