package com.consumoesperto.service;

import com.consumoesperto.dto.DescontoFixoDTO;
import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.dto.RendaConfigRequest;
import com.consumoesperto.model.RendaConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.RendaConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RendaConfigService {

    private static final TypeReference<List<DescontoFixoDTO>> LIST_DESCONTO = new TypeReference<>() {};

    private final RendaConfigRepository rendaConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<RendaConfigDTO> obterDto(Long usuarioId) {
        return rendaConfigRepository.findByUsuarioId(usuarioId).map(this::toDto);
    }

    @Transactional
    public RendaConfigDTO salvar(Long usuarioId, BigDecimal salarioBruto, List<DescontoFixoDTO> descontos, Integer diaPagamento) {
        return salvar(usuarioId, salarioBruto, descontos, diaPagamento, null);
    }

    @Transactional
    public RendaConfigDTO salvar(
        Long usuarioId,
        BigDecimal salarioBruto,
        List<DescontoFixoDTO> descontos,
        Integer diaPagamento,
        Boolean receitaAutomaticaAtiva
    ) {
        if (salarioBruto == null || salarioBruto.compareTo(BigDecimal.ZERO) < 0) {
            salarioBruto = BigDecimal.ZERO;
        }
        if (descontos == null) {
            descontos = new ArrayList<>();
        }
        List<DescontoFixoDTO> limpos = new ArrayList<>();
        for (DescontoFixoDTO d : descontos) {
            if (d == null || d.getValor() == null || d.getValor().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String rotulo = d.getRotulo() != null ? d.getRotulo().trim() : "Desconto";
            if (rotulo.isEmpty()) {
                rotulo = "Desconto";
            }
            limpos.add(new DescontoFixoDTO(rotulo, d.getValor().setScale(2, RoundingMode.HALF_UP)));
        }
        BigDecimal totalDesc = limpos.stream()
            .map(DescontoFixoDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liquido = salarioBruto.subtract(totalDesc).setScale(2, RoundingMode.HALF_UP);

        RendaConfig cfg = rendaConfigRepository.findByUsuarioId(usuarioId).orElseGet(RendaConfig::new);
        if (cfg.getId() == null) {
            Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Utilizador não encontrado"));
            cfg.setUsuario(u);
        }
        cfg.setSalarioBruto(salarioBruto.setScale(2, RoundingMode.HALF_UP));
        cfg.setSalarioLiquido(liquido);
        try {
            cfg.setDescontosFixosJson(limpos.isEmpty() ? null : objectMapper.writeValueAsString(limpos));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar descontos", e);
        }
        if (diaPagamento != null) {
            cfg.setDiaPagamento(Math.max(1, Math.min(31, diaPagamento)));
        }
        if (receitaAutomaticaAtiva != null) {
            cfg.setReceitaAutomaticaAtiva(receitaAutomaticaAtiva);
        }
        rendaConfigRepository.save(cfg);
        return toDto(cfg);
    }

    @Transactional
    public void definirReceitaAutomatica(Long usuarioId, boolean ativa) {
        RendaConfig cfg = rendaConfigRepository.findByUsuarioId(usuarioId).orElse(null);
        if (cfg == null) {
            throw new IllegalStateException("Configure primeiro a renda (salário bruto e descontos).");
        }
        cfg.setReceitaAutomaticaAtiva(ativa);
        if (!ativa) {
            cfg.setUltimoMesLancamentoAuto(null);
        }
        rendaConfigRepository.save(cfg);
    }

    private static final Pattern DIA_PAGAMENTO_NO_TEXTO = Pattern.compile(
        "(?i)\\b(?:dia\\s*(?:de\\s*)?pagamento|pagamento\\s*(?:no\\s*)?dia)\\s*:?\\s*(\\d{1,2})\\b"
            + "|\\bdia\\s+(\\d{1,2})\\b"
    );

    /**
     * Extrai dados de {@code updates} (IA) ou campos de topo do comando JSON; {@code sourceText} reforça o dia de pagamento.
     */
    @Transactional
    public RendaConfigDTO aplicarDeComandoJson(Long usuarioId, JsonNode cmd, String sourceText) {
        JsonNode updates = cmd.path("updates");
        BigDecimal bruto = readMoney(updates, "salarioBruto");
        if (bruto == null || bruto.compareTo(BigDecimal.ZERO) <= 0) {
            bruto = readMoney(updates, "bruto");
        }
        if (bruto == null || bruto.compareTo(BigDecimal.ZERO) <= 0) {
            bruto = readMoney(cmd, "amount");
        }
        if (bruto == null || bruto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Indica o salário bruto (valor numérico).");
        }
        Integer dia = readInt(updates, "diaPagamento");
        if (dia == null) {
            dia = readInt(updates, "paymentDay");
        }
        if (dia == null) {
            dia = cmd.path("dueDay").asInt(0) > 0 ? cmd.path("dueDay").asInt() : null;
        }
        if (dia == null && sourceText != null && !sourceText.isBlank()) {
            Matcher m = DIA_PAGAMENTO_NO_TEXTO.matcher(sourceText);
            if (m.find()) {
                for (int g = 1; g <= m.groupCount(); g++) {
                    String cap = m.group(g);
                    if (cap != null) {
                        try {
                            int v = Integer.parseInt(cap.trim());
                            if (v >= 1 && v <= 31) {
                                dia = v;
                                break;
                            }
                        } catch (NumberFormatException ignored) {
                            // próximo grupo
                        }
                    }
                }
            }
        }
        if (dia == null) {
            throw new IllegalArgumentException("Indica o dia de pagamento (1-31), por exemplo: *dia 5* ou *pagamento dia 10*.");
        }
        List<DescontoFixoDTO> descontos = parseDescontosArray(updates.path("descontosFixos"));
        if (descontos.isEmpty()) {
            descontos = parseDescontosArray(updates.path("descontos"));
        }
        return salvar(usuarioId, bruto, descontos, dia);
    }

    private List<DescontoFixoDTO> parseDescontosArray(JsonNode arr) {
        List<DescontoFixoDTO> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode n : arr) {
            String rotulo = firstNonBlank(
                n.path("rotulo").asText(null),
                n.path("label").asText(null),
                n.path("descricao").asText(null),
                n.path("nome").asText(null),
                n.path("tipo").asText(null)
            );
            BigDecimal v = readMoney(n, "valor");
            if (v == null) {
                v = readMoney(n, "amount");
            }
            if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                if (rotulo == null) {
                    rotulo = "Desconto";
                }
                out.add(new DescontoFixoDTO(rotulo, v));
            }
        }
        return out;
    }

    private static String firstNonBlank(String... xs) {
        if (xs == null) {
            return null;
        }
        for (String x : xs) {
            if (x != null && !x.isBlank()) {
                return x.trim();
            }
        }
        return null;
    }

    private static BigDecimal readMoney(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode()) {
            return null;
        }
        JsonNode n = parent.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        try {
            if (n.isNumber()) {
                return BigDecimal.valueOf(n.asDouble()).setScale(2, RoundingMode.HALF_UP);
            }
            String t = n.asText("").replace("R$", "").trim();
            if (t.isEmpty()) {
                return null;
            }
            if (t.matches(".*\\d+[.,]\\d{3}([.,]\\d{2})?.*") || (t.contains(",") && t.lastIndexOf(',') > t.indexOf('.'))) {
                t = t.replace(".", "").replace(",", ".");
            } else {
                t = t.replace(",", ".");
            }
            return new BigDecimal(t.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer readInt(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode()) {
            return null;
        }
        JsonNode n = parent.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isInt()) {
            return n.asInt();
        }
        try {
            int v = Integer.parseInt(n.asText("").trim());
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public RendaConfigDTO salvarDePedidoHttp(Long usuarioId, RendaConfigRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Corpo da requisição vazio.");
        }
        Integer dia = req.getDiaPagamento();
        if (dia == null) {
            dia = rendaConfigRepository.findByUsuarioId(usuarioId)
                .map(RendaConfig::getDiaPagamento)
                .orElse(null);
        }
        if (dia == null) {
            throw new IllegalArgumentException("Indique o dia de pagamento (1-31).");
        }
        Boolean auto = req.getReceitaAutomaticaAtiva();
        return salvar(
            usuarioId,
            req.getSalarioBruto() != null ? req.getSalarioBruto() : BigDecimal.ZERO,
            req.getDescontosFixos() != null ? req.getDescontosFixos() : new ArrayList<>(),
            dia,
            auto
        );
    }

    private RendaConfigDTO toDto(RendaConfig c) {
        List<DescontoFixoDTO> lista = new ArrayList<>();
        if (c.getDescontosFixosJson() != null && !c.getDescontosFixosJson().isBlank()) {
            try {
                lista = objectMapper.readValue(c.getDescontosFixosJson(), LIST_DESCONTO);
            } catch (Exception e) {
                log.warn("JSON de descontos inválido userId={}: {}", c.getUsuario() != null ? c.getUsuario().getId() : null, e.getMessage());
            }
        }
        BigDecimal bruto = c.getSalarioBruto() != null ? c.getSalarioBruto() : BigDecimal.ZERO;
        BigDecimal totalDesc = lista.stream().map(DescontoFixoDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pct = BigDecimal.ZERO;
        if (bruto.compareTo(BigDecimal.ZERO) > 0) {
            pct = totalDesc.multiply(BigDecimal.valueOf(100))
                .divide(bruto, 2, RoundingMode.HALF_UP);
        }
        return RendaConfigDTO.builder()
            .salarioBruto(bruto)
            .descontosFixos(lista)
            .diaPagamento(c.getDiaPagamento())
            .salarioLiquido(c.getSalarioLiquido() != null ? c.getSalarioLiquido() : BigDecimal.ZERO)
            .totalDescontos(totalDesc.setScale(2, RoundingMode.HALF_UP))
            .percentualDescontosSobreBruto(pct)
            .receitaAutomaticaAtiva(c.isReceitaAutomaticaAtiva())
            .build();
    }

}
