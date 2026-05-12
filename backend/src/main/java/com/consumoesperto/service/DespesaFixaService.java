package com.consumoesperto.service;

import com.consumoesperto.dto.DespesaFixaDTO;
import com.consumoesperto.dto.DespesaFixaRequest;
import com.consumoesperto.model.DespesaFixa;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.DespesaFixaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DespesaFixaService {

    private final DespesaFixaRepository despesaFixaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<DespesaFixaDTO> listar(Long usuarioId) {
        List<DespesaFixaDTO> out = new ArrayList<>();
        for (DespesaFixa d : despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(usuarioId)) {
            out.add(toDto(d));
        }
        return out;
    }

    /**
     * Soma valores cuja data efetiva de vencimento no mês de {@code referencia} ainda não ocorreu (inclui o próprio dia).
     */
    @Transactional(readOnly = true)
    public BigDecimal somarValorRestanteNoMes(Long usuarioId, LocalDate referencia) {
        int d0 = referencia.getDayOfMonth();
        YearMonth ym = YearMonth.from(referencia);
        int ultimo = ym.lengthOfMonth();
        BigDecimal sum = BigDecimal.ZERO;
        for (DespesaFixa d : despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(usuarioId)) {
            int efetivo = diaEfetivoNoMes(d.getDiaVencimento(), ultimo);
            if (efetivo >= d0) {
                sum = sum.add(nz(d.getValor()));
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Dias (do mês corrente) posteriores a {@code referencia} nos quais há saída de caixa por despesa fixa → valor agregado por dia.
     */
    @Transactional(readOnly = true)
    public Map<Integer, BigDecimal> mapaSaltosProjetadosAposDia(Long usuarioId, LocalDate referencia) {
        int d0 = referencia.getDayOfMonth();
        YearMonth ym = YearMonth.from(referencia);
        int ultimo = ym.lengthOfMonth();
        Map<Integer, BigDecimal> map = new LinkedHashMap<>();
        for (DespesaFixa d : despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(usuarioId)) {
            int efetivo = diaEfetivoNoMes(d.getDiaVencimento(), ultimo);
            if (efetivo <= d0) {
                continue;
            }
            map.merge(efetivo, nz(d.getValor()), BigDecimal::add);
        }
        for (Map.Entry<Integer, BigDecimal> e : map.entrySet()) {
            e.setValue(e.getValue().setScale(2, RoundingMode.HALF_UP));
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Optional<DespesaFixa> encontrarSimilar(Long usuarioId, String descricao, Long excludeId) {
        String n = normalizeDesc(descricao);
        if (n.length() < 2) {
            return Optional.empty();
        }
        for (DespesaFixa d : despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(usuarioId)) {
            if (excludeId != null && excludeId.equals(d.getId())) {
                continue;
            }
            String o = normalizeDesc(d.getDescricao());
            if (o.length() < 2) {
                continue;
            }
            if (o.equals(n)) {
                return Optional.of(d);
            }
            if (o.contains(n) || n.contains(o)) {
                return Optional.of(d);
            }
            int minLen = Math.min(o.length(), n.length());
            if (minLen >= 4 && levenshtein(o, n) <= 2) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    @Transactional
    public DespesaFixaDTO criar(Long usuarioId, DespesaFixaRequest req) {
        if (encontrarSimilar(usuarioId, req.getDescricao(), null).isPresent()) {
            throw new IllegalArgumentException("Já existe despesa fixa com nome semelhante.");
        }
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        DespesaFixa e = new DespesaFixa();
        e.setUsuario(u);
        aplicar(e, req);
        return toDto(despesaFixaRepository.save(e));
    }

    @Transactional
    public DespesaFixaDTO atualizar(Long usuarioId, Long id, DespesaFixaRequest req) {
        if (encontrarSimilar(usuarioId, req.getDescricao(), id).isPresent()) {
            throw new IllegalArgumentException("Já existe despesa fixa com nome semelhante.");
        }
        DespesaFixa e = despesaFixaRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Despesa fixa não encontrada."));
        if (e.getUsuario() == null || !usuarioId.equals(e.getUsuario().getId())) {
            throw new IllegalArgumentException("Despesa fixa não encontrada.");
        }
        aplicar(e, req);
        return toDto(despesaFixaRepository.save(e));
    }

    @Transactional
    public void excluir(Long usuarioId, Long id) {
        DespesaFixa e = despesaFixaRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Despesa fixa não encontrada."));
        if (e.getUsuario() == null || !usuarioId.equals(e.getUsuario().getId())) {
            throw new IllegalArgumentException("Despesa fixa não encontrada.");
        }
        despesaFixaRepository.delete(e);
    }

    private static void aplicar(DespesaFixa e, DespesaFixaRequest req) {
        e.setDescricao(req.getDescricao().trim());
        e.setValor(req.getValor().setScale(2, RoundingMode.HALF_UP));
        e.setDiaVencimento(req.getDiaVencimento());
        String cat = req.getCategoria();
        e.setCategoria(cat == null || cat.isBlank() ? null : cat.trim());
    }

    private static DespesaFixaDTO toDto(DespesaFixa d) {
        return new DespesaFixaDTO(
            d.getId(),
            d.getDescricao(),
            d.getValor(),
            d.getDiaVencimento(),
            d.getCategoria()
        );
    }

    private static int diaEfetivoNoMes(Integer diaVencimento, int ultimoDiaMes) {
        if (diaVencimento == null) {
            return 1;
        }
        return Math.min(Math.max(1, diaVencimento), ultimoDiaMes);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    static String normalizeDesc(String raw) {
        if (raw == null) {
            return "";
        }
        String n = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return n;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }
}
