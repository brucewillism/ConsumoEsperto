package com.consumoesperto.service;

import com.consumoesperto.dto.ContrachequeDTO;
import com.consumoesperto.dto.DescontoFixoDTO;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContrachequeImportado;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContrachequeImportadoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContrachequeImportService {

    private static final TypeReference<List<DescontoFixoDTO>> DESCONTO_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final DocumentoIAContextService documentoIAContextService;
    private final ContrachequeImportadoRepository contrachequeRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final ObjectMapper objectMapper;
    private final RendaConfigService rendaConfigService;
    private final TransacaoService transacaoService;
    private final ScoreService scoreService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    @Transactional
    public ContrachequeDTO processarPdf(Long usuarioId, byte[] pdfBytes) {
        return processarExtracao(usuarioId, documentoIAContextService.extrairDocumentoPdf(usuarioId, pdfBytes));
    }

    @Transactional
    public ContrachequeDTO processarExtracao(Long usuarioId, JsonNode extracted) {
        String tipo = extracted.path("tipoDocumento").asText("");
        if (!"CONTRACHEQUE".equalsIgnoreCase(tipo)) {
            throw new IllegalArgumentException("O PDF não parece ser um contracheque/holerite.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        List<DescontoFixoDTO> descontos = parseDescontos(extracted.path("descontos"));
        BigDecimal bruto = readMoney(extracted.path("salarioBruto"));
        BigDecimal liquido = readMoney(extracted.path("salarioLiquido"));
        BigDecimal totalDesc = descontos.stream().map(DescontoFixoDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (liquido.compareTo(BigDecimal.ZERO) <= 0 && bruto.compareTo(BigDecimal.ZERO) > 0) {
            liquido = bruto.subtract(totalDesc).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        ContrachequeImportado c = new ContrachequeImportado();
        c.setUsuario(usuario);
        c.setEmpresa(extracted.path("empresa").asText("Empresa não identificada"));
        c.setMes(extracted.path("mes").asInt(YearMonth.now().getMonthValue()));
        c.setAno(extracted.path("ano").asInt(YearMonth.now().getYear()));
        c.setSalarioBruto(bruto);
        c.setSalarioLiquido(liquido);
        c.setTotalDescontos(totalDesc.setScale(2, RoundingMode.HALF_UP));
        c.setDescontosJson(writeJson(descontos));
        c.setInsightsJson(writeJson(insights(extracted, bruto, totalDesc, descontos)));
        return toDto(contrachequeRepository.save(c));
    }

    @Transactional(readOnly = true)
    public List<ContrachequeDTO> listarHistorico(Long usuarioId) {
        return contrachequeRepository.findByUsuarioIdOrderByAnoDescMesDescDataCriacaoDesc(usuarioId)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ContrachequeDTO> listarPendentes(Long usuarioId) {
        return contrachequeRepository.findByUsuarioIdAndStatusOrderByDataCriacaoDesc(usuarioId, ContrachequeImportado.Status.PENDENTE)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public ContrachequeDTO confirmar(Long usuarioId, Long id) {
        ContrachequeImportado c = contrachequeRepository.findByIdAndUsuarioId(id, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Contracheque não encontrado"));
        if (c.getStatus() != ContrachequeImportado.Status.PENDENTE) {
            return toDto(c);
        }
        List<DescontoFixoDTO> descontos = readDescontos(c.getDescontosJson());
        rendaConfigService.salvar(usuarioId, c.getSalarioBruto(), descontos, 5);

        TransacaoDTO tx = new TransacaoDTO();
        tx.setDescricao("Salário " + (c.getEmpresa() != null ? c.getEmpresa() : ""));
        tx.setValor(c.getSalarioLiquido());
        tx.setTipoTransacao(TransacaoDTO.TipoTransacao.RECEITA);
        tx.setCategoriaId(resolveCategoriaSalario(usuarioId));
        tx.setDataTransacao(YearMonth.of(c.getAno(), c.getMes()).atEndOfMonth().atStartOfDay());
        tx.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        transacaoService.criarTransacao(tx, usuarioId);

        c.setStatus(ContrachequeImportado.Status.CONFIRMADO);
        c.setDataConfirmacao(LocalDateTime.now());
        scoreService.registrarEvento(usuarioId, ScoreService.EventoScore.IMPORTACAO_CONSISTENTE, "Contracheque importado em dia");
        whatsAppNotificationService.enviarParaUsuario(usuarioId,
            "Confirmei seu contracheque pelo app, atualizei sua renda e lancei a receita de salário.");
        return toDto(contrachequeRepository.save(c));
    }

    private Long resolveCategoriaSalario(Long usuarioId) {
        Categoria existente = categoriaRepository.findByUsuarioIdAndNome(usuarioId, "Salário");
        if (existente != null) {
            return existente.getId();
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        Categoria c = new Categoria();
        c.setUsuario(usuario);
        c.setNome("Salário");
        c.setDescricao("Receitas de salário/contracheque");
        c.setCor("#10b981");
        c.setIcone("money-bill-wave");
        return categoriaRepository.save(c).getId();
    }

    public ContrachequeDTO toDto(ContrachequeImportado c) {
        ContrachequeDTO dto = new ContrachequeDTO();
        dto.setId(c.getId());
        dto.setEmpresa(c.getEmpresa());
        dto.setMes(c.getMes());
        dto.setAno(c.getAno());
        dto.setSalarioBruto(c.getSalarioBruto());
        dto.setSalarioLiquido(c.getSalarioLiquido());
        dto.setTotalDescontos(c.getTotalDescontos());
        dto.setDescontos(readDescontos(c.getDescontosJson()));
        dto.setInsights(readStrings(c.getInsightsJson()));
        dto.setStatus(c.getStatus() != null ? c.getStatus().name() : null);
        dto.setDataCriacao(c.getDataCriacao());
        return dto;
    }

    private List<DescontoFixoDTO> parseDescontos(JsonNode arr) {
        List<DescontoFixoDTO> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode n : arr) {
            BigDecimal valor = readMoney(n.path("valor"));
            if (valor.compareTo(BigDecimal.ZERO) > 0) {
                out.add(new DescontoFixoDTO(n.path("rotulo").asText("Desconto"), valor));
            }
        }
        return out;
    }

    private List<String> insights(JsonNode extracted, BigDecimal bruto, BigDecimal totalDesc, List<DescontoFixoDTO> descontos) {
        List<String> ai = readStrings(extracted.path("insights").toString());
        if (!ai.isEmpty()) {
            return ai;
        }
        if (bruto.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        BigDecimal pct = totalDesc.multiply(BigDecimal.valueOf(100)).divide(bruto, 2, RoundingMode.HALF_UP);
        DescontoFixoDTO maior = descontos.stream().max((a, b) -> a.getValor().compareTo(b.getValor())).orElse(null);
        String maiorTxt = maior != null ? " O item " + maior.getRotulo() + " foi o maior impacto este mês." : "";
        return List.of("Seus descontos representam " + pct + "% do salário bruto." + maiorTxt);
    }

    private List<DescontoFixoDTO> readDescontos(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, DESCONTO_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> readStrings(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar contracheque", e);
        }
    }

    private static BigDecimal readMoney(JsonNode n) {
        try {
            if (n == null || n.isMissingNode() || n.isNull()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (n.isNumber()) return BigDecimal.valueOf(n.asDouble()).setScale(2, RoundingMode.HALF_UP);
            String t = n.asText("").replace("R$", "").trim();
            if (t.contains(",")) t = t.replace(".", "").replace(",", ".");
            return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
