package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.ingest.notificacao.parser.NotificacaoBancariaParser;
import com.consumoesperto.model.IngestFonteRecurso;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.IngestFonteRecursoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IngestFonteRecursoService {

    private final IngestFonteRecursoRepository repository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;

    @Transactional(readOnly = true)
    public List<IngestFonteRecurso> listar(Long usuarioId) {
        return repository.findByUsuarioIdOrderByAppAscCanalAsc(usuarioId);
    }

    @Transactional(readOnly = true)
    public Optional<IngestFonteRecurso> resolver(Long usuarioId, String app, String canal) {
        if (app == null || canal == null) {
            return Optional.empty();
        }
        return repository.findByUsuarioIdAndAppIgnoreCaseAndCanalIgnoreCase(usuarioId, app, canal);
    }

    @Transactional
    public IngestFonteRecurso upsert(
        Long usuarioId, String app, String canal, Long contaBancariaId, Long cartaoCreditoId
    ) {
        if (app == null || app.isBlank() || canal == null || canal.isBlank()) {
            throw new IllegalArgumentException("App e canal são obrigatórios");
        }
        String appNorm = app.trim().toLowerCase();
        String canalNorm = canal.trim().toUpperCase();
        if (!NotificacaoBancariaParser.CANAL_CREDITO.equals(canalNorm)
            && !NotificacaoBancariaParser.CANAL_DEBITO.equals(canalNorm)
            && !NotificacaoBancariaParser.CANAL_PIX.equals(canalNorm)) {
            throw new IllegalArgumentException("Canal inválido");
        }
        if (contaBancariaId != null) {
            contaBancariaRepository.findByIdAndUsuarioId(contaBancariaId, usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Conta inválida"));
        }
        if (cartaoCreditoId != null) {
            cartaoCreditoRepository.findByIdAndUsuarioId(cartaoCreditoId, usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Cartão inválido"));
        }
        IngestFonteRecurso row = repository
            .findByUsuarioIdAndAppIgnoreCaseAndCanalIgnoreCase(usuarioId, appNorm, canalNorm)
            .orElseGet(IngestFonteRecurso::new);
        row.setUsuarioId(usuarioId);
        row.setApp(appNorm);
        row.setCanal(canalNorm);
        row.setContaBancariaId(contaBancariaId);
        row.setCartaoCreditoId(cartaoCreditoId);
        return repository.save(row);
    }

    @Transactional
    public void remover(Long usuarioId, Long id) {
        IngestFonteRecurso row = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Mapeamento não encontrado"));
        if (!usuarioId.equals(row.getUsuarioId())) {
            throw new IllegalArgumentException("Mapeamento não encontrado");
        }
        repository.delete(row);
    }
}
