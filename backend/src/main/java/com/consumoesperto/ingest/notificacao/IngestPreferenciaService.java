package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.config.IngestNotificacaoProperties;
import com.consumoesperto.model.IngestPreferencia;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.IngestPreferenciaRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class IngestPreferenciaService {

    private final IngestPreferenciaRepository repository;
    private final IngestNotificacaoProperties properties;
    private final ContaBancariaRepository contaBancariaRepository;

    @Transactional
    public IngestPreferencia obterOuCriar(Long usuarioId) {
        return repository.findById(usuarioId).orElseGet(() -> {
            IngestPreferencia p = new IngestPreferencia();
            p.setUsuarioId(usuarioId);
            p.setAtivo(true);
            p.setAgrupamento(IngestPreferencia.AGRUPAMENTO_IMEDIATO);
            p.setResumoMinutos(15);
            p.setSilenciosoInicio(parseTime(properties.getSilenciosoInicioDefault()));
            p.setSilenciosoFim(parseTime(properties.getSilenciosoFimDefault()));
            p.setAtualizadoEm(AppTimeZone.agora());
            return repository.save(p);
        });
    }

    @Transactional
    public IngestPreferencia atualizar(
        Long usuarioId,
        Boolean ativo,
        String agrupamento,
        Integer resumoMinutos,
        LocalTime silenciosoInicio,
        LocalTime silenciosoFim,
        Long contaPadraoId
    ) {
        IngestPreferencia p = obterOuCriar(usuarioId);
        if (ativo != null) {
            p.setAtivo(ativo);
        }
        if (agrupamento != null && !agrupamento.isBlank()) {
            String ag = agrupamento.trim().toUpperCase();
            if (!IngestPreferencia.AGRUPAMENTO_IMEDIATO.equals(ag)
                && !IngestPreferencia.AGRUPAMENTO_RESUMO.equals(ag)) {
                throw new IllegalArgumentException("Agrupamento inválido");
            }
            p.setAgrupamento(ag);
        }
        if (resumoMinutos != null) {
            p.setResumoMinutos(Math.max(1, Math.min(180, resumoMinutos)));
        }
        if (silenciosoInicio != null) {
            p.setSilenciosoInicio(silenciosoInicio);
        }
        if (silenciosoFim != null) {
            p.setSilenciosoFim(silenciosoFim);
        }
        if (contaPadraoId != null) {
            contaBancariaRepository.findByIdAndUsuarioId(contaPadraoId, usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Conta padrão inválida"));
            p.setContaPadraoId(contaPadraoId);
        }
        p.setAtualizadoEm(AppTimeZone.agora());
        return repository.save(p);
    }

    static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalTime.parse(raw.trim());
    }
}
