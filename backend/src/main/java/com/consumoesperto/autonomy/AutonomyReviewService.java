package com.consumoesperto.autonomy;

import com.consumoesperto.model.AutonomyReviewItem;
import com.consumoesperto.repository.AutonomyReviewItemRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AutonomyReviewService {

    private final AutonomyReviewItemRepository repository;

    @Transactional
    public AutonomyReviewItem open(
        Long usuarioId,
        String kind,
        Long transacaoId,
        String title,
        String detail,
        BigDecimal confidence
    ) {
        if (transacaoId != null) {
            var dup = repository.findFirstByUsuarioIdAndKindAndTransacaoIdAndStatus(
                usuarioId, kind, transacaoId, AutonomyReviewItem.OPEN);
            if (dup.isPresent()) {
                return dup.get();
            }
        } else if (title != null) {
            String t = title.length() > 200 ? title.substring(0, 200) : title;
            var dup = repository.findFirstByUsuarioIdAndKindAndTitleAndStatus(
                usuarioId, kind, t, AutonomyReviewItem.OPEN);
            if (dup.isPresent()) {
                return dup.get();
            }
        }
        AutonomyReviewItem item = new AutonomyReviewItem();
        item.setUsuarioId(usuarioId);
        item.setKind(kind);
        item.setTransacaoId(transacaoId);
        item.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        item.setDetail(detail);
        item.setConfidence(confidence);
        item.setStatus(AutonomyReviewItem.OPEN);
        item.setCreatedAt(AppTimeZone.agora());
        return repository.save(item);
    }

    @Transactional(readOnly = true)
    public List<AutonomyReviewItem> listOpen(Long usuarioId) {
        return repository.findByUsuarioIdAndStatusOrderByCreatedAtDesc(usuarioId, AutonomyReviewItem.OPEN);
    }

    @Transactional(readOnly = true)
    public long countOpen(Long usuarioId) {
        return repository.countByUsuarioIdAndStatus(usuarioId, AutonomyReviewItem.OPEN);
    }

    @Transactional
    public void resolve(Long usuarioId, Long itemId) {
        repository.findById(itemId).ifPresent(item -> {
            if (!usuarioId.equals(item.getUsuarioId())) {
                return;
            }
            item.setStatus(AutonomyReviewItem.RESOLVED);
            item.setResolvedAt(AppTimeZone.agora());
            repository.save(item);
        });
    }
}
