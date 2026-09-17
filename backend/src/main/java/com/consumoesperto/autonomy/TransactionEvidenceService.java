package com.consumoesperto.autonomy;

import com.consumoesperto.model.TransactionEvidence;
import com.consumoesperto.repository.TransactionEvidenceRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionEvidenceService {

    private final TransactionEvidenceRepository repository;

    @Transactional
    public TransactionEvidence record(
        Long usuarioId,
        Long transacaoId,
        String source,
        String externalId,
        String sourceEventId,
        BigDecimal confidence,
        String metadataMin
    ) {
        if (transacaoId != null && source != null) {
            var byTxSource = repository.findFirstByTransacaoIdAndSource(transacaoId, source);
            if (byTxSource.isPresent()) {
                return byTxSource.get();
            }
        }
        if (externalId != null && !externalId.isBlank()) {
            var existing = repository.findFirstByUsuarioIdAndSourceAndExternalId(usuarioId, source, externalId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        TransactionEvidence ev = new TransactionEvidence();
        ev.setUsuarioId(usuarioId);
        ev.setTransacaoId(transacaoId);
        ev.setSource(source);
        ev.setExternalId(externalId);
        ev.setSourceEventId(sourceEventId);
        ev.setConfidence(confidence);
        ev.setFirstSeenAt(AppTimeZone.agora());
        ev.setConfirmedAt(AppTimeZone.agora());
        ev.setMetadataMin(metadataMin);
        return repository.save(ev);
    }

    @Transactional(readOnly = true)
    public List<String> sources(Long transacaoId) {
        return repository.findByTransacaoIdOrderByFirstSeenAtAsc(transacaoId).stream()
            .map(TransactionEvidence::getSource)
            .distinct()
            .toList();
    }
}
