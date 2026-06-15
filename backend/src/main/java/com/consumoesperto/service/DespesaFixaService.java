package com.consumoesperto.service;

import com.consumoesperto.dto.DespesaFixaDTO;
import com.consumoesperto.dto.DespesaFixaRequest;
import com.consumoesperto.model.DespesaFixa;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.DespesaFixaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
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
@Slf4j
public class DespesaFixaService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final int DIAS_ANTECEDENCIA_LEMBRETE = 3;

    private final DespesaFixaRepository despesaFixaRepository;
    private final UsuarioRepository usuarioRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final JarvisProtocolService jarvisProtocolService;

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
    public List<DespesaFixa> encontrarPorIdentificador(Long usuarioId, String identificador) {
        String n = normalizeDesc(identificador);
        if (n.length() < 2) {
            return List.of();
        }
        List<DespesaFixa> out = new ArrayList<>();
        for (DespesaFixa d : despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(usuarioId)) {
            if (correspondeIdentificador(n, normalizeDesc(d.getDescricao()))) {
                out.add(d);
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<DespesaFixa> encontrarSimilar(Long usuarioId, String descricao, Long excludeId) {
        return encontrarPorIdentificador(usuarioId, descricao).stream()
            .filter(d -> excludeId == null || !excludeId.equals(d.getId()))
            .findFirst();
    }

    @Transactional(readOnly = true)
    public DespesaFixaDTO buscar(Long usuarioId, Long id) {
        DespesaFixa e = despesaFixaRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Despesa fixa não encontrada."));
        if (e.getUsuario() == null || !usuarioId.equals(e.getUsuario().getId())) {
            throw new IllegalArgumentException("Despesa fixa não encontrada.");
        }
        return toDto(e);
    }

    @Transactional(readOnly = true)
    public BigDecimal somarValorMensal(Long usuarioId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (DespesaFixa d : despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(usuarioId)) {
            sum = sum.add(nz(d.getValor()));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean correspondeIdentificador(String token, String nome) {
        if (nome.length() < 2) {
            return false;
        }
        if (nome.equals(token) || nome.contains(token) || token.contains(nome)) {
            return true;
        }
        int minLen = Math.min(nome.length(), token.length());
        return minLen >= 4 && levenshtein(nome, token) <= 2;
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

    @Transactional(readOnly = true)
    public List<DespesaFixaDTO> listarVencendoEmDias(Long usuarioId, int dias) {
        LocalDate hoje = LocalDate.now();
        List<DespesaFixaDTO> out = new ArrayList<>();
        for (DespesaFixa d : despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(usuarioId)) {
            if (venceEmDias(d, hoje, dias)) {
                out.add(toDto(d));
            }
        }
        return out;
    }

    /** Job diário: lembrete WhatsApp 3 dias antes do vencimento de despesas fixas. */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void alertarVencimentosProximos() {
        LocalDate hoje = LocalDate.now();
        List<DespesaFixa> todas = despesaFixaRepository.findAll();
        if (todas.isEmpty()) {
            return;
        }
        log.info("[DESPESA_FIXA] Verificando {} despesa(s) fixa(s) — lembrete {} dias antes", todas.size(), DIAS_ANTECEDENCIA_LEMBRETE);
        for (DespesaFixa d : todas) {
            if (!venceEmDias(d, hoje, DIAS_ANTECEDENCIA_LEMBRETE)) {
                continue;
            }
            Usuario u = d.getUsuario();
            if (u == null || u.getWhatsappNumero() == null || u.getWhatsappNumero().isBlank()) {
                continue;
            }
            try {
                int diaEfetivo = diaEfetivoNoMes(d.getDiaVencimento(), YearMonth.from(hoje.plusDays(DIAS_ANTECEDENCIA_LEMBRETE)).lengthOfMonth());
                String voc = jarvisProtocolService.resolveVocative(u.getId(), usuarioRepository);
                String msg = jarvisProtocolService.lembreteDespesaFixaVencimento(
                    voc,
                    d.getDescricao(),
                    BRL.format(nz(d.getValor())),
                    DIAS_ANTECEDENCIA_LEMBRETE,
                    diaEfetivo
                );
                whatsAppNotificationService.enviarParaUsuario(u.getId(), msg);
            } catch (Exception e) {
                log.warn("[DESPESA_FIXA] Falha lembrete id={} userId={}: {}", d.getId(), u.getId(), e.getMessage());
            }
        }
    }

    public static boolean venceEmDias(DespesaFixa d, LocalDate hoje, int diasAntecedencia) {
        if (d == null) {
            return false;
        }
        return VencimentoMensalUtil.venceEmDias(d.getDiaVencimento(), hoje, diasAntecedencia);
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
        return VencimentoMensalUtil.diaEfetivoNoMes(diaVencimento, ultimoDiaMes);
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
