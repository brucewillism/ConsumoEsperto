package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.model.AutonomyPreferencia;
import com.consumoesperto.repository.AutonomyPreferenciaRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class AutonomyPreferenciaService {

    private final AutonomyPreferenciaRepository repository;
    private final FinancialAutonomyProperties properties;

    @Transactional
    public AutonomyPreferencia obterOuCriar(Long usuarioId) {
        return repository.findById(usuarioId).orElseGet(() -> {
            AutonomyPreferencia p = new AutonomyPreferencia();
            p.setUsuarioId(usuarioId);
            p.setNivel(AutonomyLevel.ASSISTED.name());
            p.setSilenciosoInicio(parseTime(properties.getSilenciosoInicioDefault()));
            p.setSilenciosoFim(parseTime(properties.getSilenciosoFimDefault()));
            p.setAtualizadoEm(AppTimeZone.agora());
            return repository.save(p);
        });
    }

    @Transactional
    public AutonomyPreferencia salvar(Long usuarioId, AutonomyPreferencia incoming) {
        AutonomyPreferencia p = obterOuCriar(usuarioId);
        if (incoming.getNivel() != null && !incoming.getNivel().isBlank()) {
            p.setNivel(AutonomyLevel.valueOf(incoming.getNivel().trim().toUpperCase()).name());
        }
        p.setRegistrarAuto(incoming.isRegistrarAuto());
        p.setClassificarAuto(incoming.isClassificarAuto());
        p.setAprenderCategorias(incoming.isAprenderCategorias());
        p.setDetectarAssinaturas(incoming.isDetectarAssinaturas());
        p.setDetectarDuplicatas(incoming.isDetectarDuplicatas());
        p.setDetectarAnomalias(incoming.isDetectarAnomalias());
        p.setPreverSaldo(incoming.isPreverSaldo());
        p.setJarvisProativo(incoming.isJarvisProativo());
        p.setResumoDiario(incoming.isResumoDiario());
        p.setResumoSemanal(incoming.isResumoSemanal());
        if (incoming.getSilenciosoInicio() != null) {
            p.setSilenciosoInicio(incoming.getSilenciosoInicio());
        }
        if (incoming.getSilenciosoFim() != null) {
            p.setSilenciosoFim(incoming.getSilenciosoFim());
        }
        p.setAtualizadoEm(AppTimeZone.agora());
        return repository.save(p);
    }

    public AutonomyLevel levelOf(AutonomyPreferencia p) {
        if (p == null || p.getNivel() == null) {
            return AutonomyLevel.ASSISTED;
        }
        try {
            return AutonomyLevel.valueOf(p.getNivel());
        } catch (IllegalArgumentException e) {
            return AutonomyLevel.ASSISTED;
        }
    }

    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalTime.parse(raw.trim());
    }
}
