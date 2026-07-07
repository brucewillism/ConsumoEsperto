package com.consumoesperto.service.jarvis;

import com.consumoesperto.model.MemoriaCategoriaOrigem;
import com.consumoesperto.model.MemoriaMetadados;
import com.consumoesperto.model.MemoriaTipo;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.service.CerebroSemanticoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoriaCorrecaoMemoriaService {

    private final CerebroSemanticoService cerebroSemanticoService;
    private final CategoriaRepository categoriaRepository;

    public void registrarCorrecaoCategoria(Long userId, String descricaoTransacao, Long categoriaIdNova) {
        if (userId == null || descricaoTransacao == null || descricaoTransacao.isBlank() || categoriaIdNova == null) {
            return;
        }
        String catNome = categoriaRepository.findById(categoriaIdNova)
            .map(c -> c.getNome())
            .orElse("Categoria");
        String ctx = "Correção: despesas com descrição «" + descricaoTransacao.trim()
            + "» devem usar categoria «" + catNome + "» (categoriaId=" + categoriaIdNova + ").";
        try {
            cerebroSemanticoService.gravarMemoria(
                userId, ctx, MemoriaCategoriaOrigem.FINANCAS,
                MemoriaMetadados.sistema(MemoriaTipo.CORRECAO));
        } catch (Exception e) {
            log.debug("Falha ao gravar CORRECAO categoria userId={}: {}", userId, e.getMessage());
        }
    }

    public Optional<Long> sugerirCategoriaPorCorrecao(Long userId, String descricao) {
        if (userId == null || descricao == null || descricao.isBlank()) {
            return Optional.empty();
        }
        String norm = normalize(descricao);
        return cerebroSemanticoService.listarRecentesParaUsuario(userId, 40).stream()
            .filter(m -> MemoriaTipo.CORRECAO.name().equalsIgnoreCase(m.getTipo()))
            .filter(m -> m.getContexto() != null && normalize(m.getContexto()).contains(extrairToken(norm)))
            .findFirst()
            .flatMap(m -> extrairCategoriaId(m.getContexto()));
    }

    private static Optional<Long> extrairCategoriaId(String ctx) {
        if (ctx == null) {
            return Optional.empty();
        }
        int idx = ctx.indexOf("categoriaId=");
        if (idx < 0) {
            return Optional.empty();
        }
        String tail = ctx.substring(idx + "categoriaId=".length()).trim();
        int end = 0;
        while (end < tail.length() && Character.isDigit(tail.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(tail.substring(0, end)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String extrairToken(String norm) {
        if (norm.length() <= 12) {
            return norm;
        }
        return norm.substring(0, 12);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}
