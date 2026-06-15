package com.consumoesperto.service;

import com.consumoesperto.dto.DescontoFixoDTO;
import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.dto.RendaConfigRequest;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.RendaConfig;
import com.consumoesperto.model.TipoConfiguracaoRenda;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.RendaConfigRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class RendaConfigService {

    private static final TypeReference<List<DescontoFixoDTO>> LIST_DESCONTO = new TypeReference<>() {};

    private final RendaConfigRepository rendaConfigRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransacaoRepository transacaoRepository;
    private final ObjectMapper objectMapper;
    private final SalarioAutomaticoService salarioAutomaticoService;

    public RendaConfigService(
        RendaConfigRepository rendaConfigRepository,
        ContaBancariaRepository contaBancariaRepository,
        UsuarioRepository usuarioRepository,
        TransacaoRepository transacaoRepository,
        ObjectMapper objectMapper,
        @Lazy SalarioAutomaticoService salarioAutomaticoService
    ) {
        this.rendaConfigRepository = rendaConfigRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.usuarioRepository = usuarioRepository;
        this.transacaoRepository = transacaoRepository;
        this.objectMapper = objectMapper;
        this.salarioAutomaticoService = salarioAutomaticoService;
    }

    @Transactional(readOnly = true)
    public Optional<RendaConfigDTO> obterDto(Long usuarioId) {
        return rendaConfigRepository.findByUsuarioId(usuarioId).map(this::toDto);
    }

    /**
     * Renda mensal usada em projeções, dashboard e J.A.R.V.I.S. — respeita {@link TipoConfiguracaoRenda}.
     */
    @Transactional(readOnly = true)
    public BigDecimal getRendaMensalEstimada(Long usuarioId) {
        if (usuarioId == null) {
            return BigDecimal.ZERO;
        }
        return rendaConfigRepository.findByUsuarioId(usuarioId)
            .map(cfg -> calcularRendaMensal(cfg, usuarioId))
            .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calcularRendaMensal(RendaConfig config, Long usuarioId) {
        TipoConfiguracaoRenda tipo = config.getTipoConfiguracaoRenda() != null
            ? config.getTipoConfiguracaoRenda()
            : TipoConfiguracaoRenda.CONTRACHEQUE;
        return switch (tipo) {
            case CONTRACHEQUE -> calcularRendaLiquidaContracheque(config);
            case RECEBIMENTO_UNICO -> config.getValorRecebimentoUnico() != null
                ? config.getValorRecebimentoUnico()
                : BigDecimal.ZERO;
            case FLUXO_DIARIO -> calcularMediaMovel30Dias(usuarioId);
        };
    }

    private BigDecimal calcularRendaLiquidaContracheque(RendaConfig config) {
        BigDecimal liquido = config.getSalarioLiquido();
        if (liquido != null && liquido.compareTo(BigDecimal.ZERO) > 0) {
            return liquido.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal bruto = config.getSalarioBruto() != null ? config.getSalarioBruto() : BigDecimal.ZERO;
        if (bruto.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        List<DescontoFixoDTO> descontos = parseDescontosJson(config.getDescontosFixosJson());
        BigDecimal totalDesc = descontos.stream()
            .map(DescontoFixoDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return bruto.subtract(totalDesc).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularMediaMovel30Dias(Long usuarioId) {
        return calcularMediaMovelReal(usuarioId, 30);
    }

    /**
     * Média móvel real de receitas confirmadas — janela em dias civis (ex.: 90 para feedback WhatsApp).
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularMediaMovelReal(Long usuarioId, int dias) {
        if (usuarioId == null || dias <= 0) {
            return BigDecimal.ZERO;
        }
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicio = hoje.minusDays(dias).atStartOfDay();
        LocalDateTime fim = hoje.atTime(23, 59, 59);
        BigDecimal totalReceitas = transacaoRepository.sumReceitasConfirmadasPeriodo(usuarioId, inicio, fim);
        if (totalReceitas == null || totalReceitas.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalReceitas.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularMediaMovel90Dias(Long usuarioId) {
        return calcularMediaMovelReal(usuarioId, 90);
    }

    static String rotuloPorTipo(TipoConfiguracaoRenda tipo) {
        if (tipo == null) {
            return "Salário líquido";
        }
        return switch (tipo) {
            case CONTRACHEQUE -> "Salário líquido";
            case RECEBIMENTO_UNICO -> "Recebimento mensal";
            case FLUXO_DIARIO -> "Média 30 dias";
        };
    }

    private List<DescontoFixoDTO> parseDescontosJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, LIST_DESCONTO);
        } catch (Exception e) {
            return new ArrayList<>();
        }
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
        return salvar(usuarioId, salarioBruto, descontos, diaPagamento, receitaAutomaticaAtiva, null);
    }

    @Transactional
    public RendaConfigDTO salvar(
        Long usuarioId,
        BigDecimal salarioBruto,
        List<DescontoFixoDTO> descontos,
        Integer diaPagamento,
        Boolean receitaAutomaticaAtiva,
        Long contaBancariaId
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
        cfg.setTipoConfiguracaoRenda(TipoConfiguracaoRenda.CONTRACHEQUE);
        cfg.setValorRecebimentoUnico(null);
        try {
            cfg.setDescontosFixosJson(limpos.isEmpty() ? null : objectMapper.writeValueAsString(limpos));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar descontos", e);
        }
        if (diaPagamento != null) {
            cfg.setDiaPagamento(Math.max(1, Math.min(31, diaPagamento)));
        } else if (cfg.getDiaPagamento() == null) {
            cfg.setDiaPagamento(SalarioAutomaticoService.DIA_PAGAMENTO_PADRAO);
        }
        if (contaBancariaId != null) {
            ContaBancaria conta = contaBancariaRepository.findByIdAndUsuarioId(contaBancariaId, usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Conta bancária não encontrada."));
            cfg.setContaBancaria(conta);
        }
        boolean registroNovo = cfg.getId() == null;
        if (receitaAutomaticaAtiva != null) {
            cfg.setReceitaAutomaticaAtiva(receitaAutomaticaAtiva);
        } else if (registroNovo && liquido.compareTo(BigDecimal.ZERO) > 0) {
            // Primeira configuração com líquido > 0: lançamento mensal automático por omissão.
            cfg.setReceitaAutomaticaAtiva(true);
        }
        rendaConfigRepository.save(cfg);
        if (cfg.isReceitaAutomaticaAtiva()) {
            try {
                salarioAutomaticoService.tentarLancarSalarioMesAtual(cfg);
            } catch (Exception e) {
                log.warn("Catch-up salário automático após salvar renda userId={}: {}", usuarioId, e.getMessage());
            }
        }
        return toDto(cfg);
    }

    @Transactional(readOnly = true)
    public Optional<Integer> obterUltimoMesLancamentoAutomatico(Long usuarioId) {
        return rendaConfigRepository.findByUsuarioId(usuarioId)
            .map(RendaConfig::getUltimoMesLancamentoAuto);
    }

    /** Marca o mês civil (aaaaMM) como já tendo recebido o salário automático — evita duplicar com contracheque no mesmo mês. */
    @Transactional
    public void marcarMesSalarialAutomaticoLancado(Long usuarioId, YearMonth mes) {
        if (mes == null) {
            return;
        }
        RendaConfig cfg = rendaConfigRepository.findByUsuarioId(usuarioId).orElse(null);
        if (cfg == null) {
            return;
        }
        int ym = mes.getYear() * 100 + mes.getMonthValue();
        cfg.setUltimoMesLancamentoAuto(ym);
        rendaConfigRepository.save(cfg);
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
        if (ativa) {
            try {
                salarioAutomaticoService.tentarLancarSalarioMesAtual(cfg);
            } catch (Exception e) {
                log.warn("Catch-up salário automático userId={}: {}", usuarioId, e.getMessage());
            }
        }
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

    /**
     * Aplica perfil híbrido de renda (SET_INCOME_PROFILE) a partir do JSON da IA.
     */
    @Transactional
    public RendaConfigDTO aplicarPerfilIncomeDeComandoJson(Long usuarioId, JsonNode cmd, String sourceText) {
        TipoConfiguracaoRenda tipo = resolverTipoPerfil(cmd);
        if (tipo == null) {
            throw new IllegalArgumentException(
                "Indica o perfil de renda: *contracheque*, *recebimento único* ou *fluxo diário*.");
        }
        return switch (tipo) {
            case CONTRACHEQUE -> aplicarContrachequeDeComando(cmd, sourceText, usuarioId);
            case RECEBIMENTO_UNICO -> aplicarRecebimentoUnicoDeComando(cmd, sourceText, usuarioId);
            case FLUXO_DIARIO -> aplicarFluxoDiarioDeComando(cmd, usuarioId);
        };
    }

    private TipoConfiguracaoRenda resolverTipoPerfil(JsonNode cmd) {
        String raw = firstNonBlank(
            cmd.path("tipoPerfil").asText(null),
            cmd.path("tipoConfiguracao").asText(null),
            cmd.path("tipoConfiguracaoRenda").asText(null),
            cmd.path("updates").path("tipoPerfil").asText(null),
            cmd.path("updates").path("tipoConfiguracao").asText(null)
        );
        if (raw == null) {
            return null;
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT)
            .replace(' ', '_')
            .replace('-', '_');
        if (norm.contains("FLUXO") || norm.contains("DIARIO") || norm.contains("DIÁRIO")
            || norm.contains("VARIAVEL") || norm.contains("PIX")) {
            return TipoConfiguracaoRenda.FLUXO_DIARIO;
        }
        if (norm.contains("UNICO") || norm.contains("ÚNICO") || norm.contains("FIXO")) {
            return TipoConfiguracaoRenda.RECEBIMENTO_UNICO;
        }
        if (norm.contains("CONTRACHEQUE") || norm.contains("HOLERITE") || norm.contains("CLT") || norm.contains("SALARIO")) {
            return TipoConfiguracaoRenda.CONTRACHEQUE;
        }
        try {
            return TipoConfiguracaoRenda.valueOf(norm);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private RendaConfigDTO aplicarContrachequeDeComando(JsonNode cmd, String sourceText, Long usuarioId) {
        JsonNode updates = cmd.path("updates");
        BigDecimal bruto = readMoney(cmd, "salarioBruto");
        if (bruto == null) {
            bruto = readMoney(updates, "salarioBruto");
        }
        if (bruto == null || bruto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Indica o salário bruto (valor numérico).");
        }
        Integer dia = readInt(cmd, "diaRecebimento");
        if (dia == null) {
            dia = readInt(updates, "diaRecebimento");
        }
        if (dia == null) {
            dia = readInt(updates, "diaPagamento");
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
            throw new IllegalArgumentException("Indica o dia de recebimento (1-31), por exemplo: *dia 5*.");
        }
        List<DescontoFixoDTO> descontos = parseDescontosArray(updates.path("descontosFixos"));
        if (descontos.isEmpty()) {
            descontos = parseDescontosArray(updates.path("descontos"));
        }
        BigDecimal totalDesc = readMoney(cmd, "descontosHolerite");
        if (totalDesc == null) {
            totalDesc = readMoney(updates, "descontosHolerite");
        }
        if (totalDesc != null && totalDesc.compareTo(BigDecimal.ZERO) > 0 && descontos.isEmpty()) {
            descontos.add(new DescontoFixoDTO("Descontos holerite", totalDesc));
        }
        return salvar(usuarioId, bruto, descontos, dia);
    }

    private RendaConfigDTO aplicarRecebimentoUnicoDeComando(JsonNode cmd, String sourceText, Long usuarioId) {
        JsonNode updates = cmd.path("updates");
        BigDecimal valor = readMoney(cmd, "valorLiquidoFixo");
        if (valor == null) {
            valor = readMoney(updates, "valorLiquidoFixo");
        }
        if (valor == null) {
            valor = readMoney(cmd, "amount");
        }
        if (valor == null) {
            valor = readMoney(updates, "valorRecebimentoUnico");
        }
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Indica o valor líquido fixo mensal (valor numérico).");
        }
        Integer dia = readInt(cmd, "diaRecebimentoFixo");
        if (dia == null) {
            dia = readInt(updates, "diaRecebimentoFixo");
        }
        if (dia == null) {
            dia = readInt(updates, "diaPagamento");
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
            dia = SalarioAutomaticoService.DIA_PAGAMENTO_PADRAO;
        }
        RendaConfigRequest req = new RendaConfigRequest();
        req.setTipoConfiguracaoRenda(TipoConfiguracaoRenda.RECEBIMENTO_UNICO);
        req.setValorRecebimentoUnico(valor);
        req.setDiaPagamento(dia);
        return salvarRecebimentoUnico(usuarioId, req);
    }

    private RendaConfigDTO aplicarFluxoDiarioDeComando(JsonNode cmd, Long usuarioId) {
        JsonNode updates = cmd.path("updates");
        BigDecimal meta = readMoney(cmd, "metaFaturamentoMensal");
        if (meta == null) {
            meta = readMoney(updates, "metaFaturamentoMensal");
        }
        if (meta == null) {
            meta = readMoney(cmd, "amount");
        }
        if (meta == null) {
            meta = readMoney(updates, "amount");
        }
        RendaConfigRequest req = new RendaConfigRequest();
        req.setTipoConfiguracaoRenda(TipoConfiguracaoRenda.FLUXO_DIARIO);
        req.setMetaFaturamentoMensal(meta);
        return salvarFluxoDiario(usuarioId, req);
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
        TipoConfiguracaoRenda tipo = req.getTipoConfiguracaoRenda();
        if (tipo == null) {
            tipo = rendaConfigRepository.findByUsuarioId(usuarioId)
                .map(RendaConfig::getTipoConfiguracaoRenda)
                .orElse(TipoConfiguracaoRenda.CONTRACHEQUE);
        }
        return switch (tipo) {
            case RECEBIMENTO_UNICO -> salvarRecebimentoUnico(usuarioId, req);
            case FLUXO_DIARIO -> salvarFluxoDiario(usuarioId, req);
            case CONTRACHEQUE -> salvarContrachequeDePedido(usuarioId, req);
        };
    }

    private RendaConfigDTO salvarContrachequeDePedido(Long usuarioId, RendaConfigRequest req) {
        Integer dia = req.getDiaPagamento();
        if (dia == null) {
            dia = rendaConfigRepository.findByUsuarioId(usuarioId)
                .map(RendaConfig::getDiaPagamento)
                .orElse(null);
        }
        if (dia == null) {
            dia = SalarioAutomaticoService.DIA_PAGAMENTO_PADRAO;
        }
        Boolean auto = req.getReceitaAutomaticaAtiva();
        return salvar(
            usuarioId,
            req.getSalarioBruto() != null ? req.getSalarioBruto() : BigDecimal.ZERO,
            req.getDescontosFixos() != null ? req.getDescontosFixos() : new ArrayList<>(),
            dia,
            auto,
            req.getContaBancariaId()
        );
    }

    private RendaConfigDTO salvarRecebimentoUnico(Long usuarioId, RendaConfigRequest req) {
        BigDecimal valor = req.getValorRecebimentoUnico();
        if (valor != null && valor.compareTo(BigDecimal.ZERO) < 0) {
            valor = BigDecimal.ZERO;
        }
        if (valor != null) {
            valor = valor.setScale(2, RoundingMode.HALF_UP);
        }
        Integer dia = req.getDiaPagamento();
        if (dia == null) {
            dia = rendaConfigRepository.findByUsuarioId(usuarioId)
                .map(RendaConfig::getDiaPagamento)
                .orElse(SalarioAutomaticoService.DIA_PAGAMENTO_PADRAO);
        }

        RendaConfig cfg = rendaConfigRepository.findByUsuarioId(usuarioId).orElseGet(RendaConfig::new);
        if (cfg.getId() == null) {
            Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Utilizador não encontrado"));
            cfg.setUsuario(u);
        }
        cfg.setTipoConfiguracaoRenda(TipoConfiguracaoRenda.RECEBIMENTO_UNICO);
        cfg.setValorRecebimentoUnico(valor);
        cfg.setDescontosFixosJson(null);
        BigDecimal efetivo = valor != null ? valor : BigDecimal.ZERO;
        cfg.setSalarioBruto(efetivo);
        cfg.setSalarioLiquido(efetivo);
        cfg.setDiaPagamento(Math.max(1, Math.min(31, dia)));
        if (req.getContaBancariaId() != null) {
            ContaBancaria conta = contaBancariaRepository.findByIdAndUsuarioId(req.getContaBancariaId(), usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Conta bancária não encontrada."));
            cfg.setContaBancaria(conta);
        }
        if (req.getReceitaAutomaticaAtiva() != null) {
            cfg.setReceitaAutomaticaAtiva(req.getReceitaAutomaticaAtiva());
        } else if (cfg.getId() == null && efetivo.compareTo(BigDecimal.ZERO) > 0) {
            cfg.setReceitaAutomaticaAtiva(true);
        }
        rendaConfigRepository.save(cfg);
        if (cfg.isReceitaAutomaticaAtiva()) {
            try {
                salarioAutomaticoService.tentarLancarSalarioMesAtual(cfg);
            } catch (Exception e) {
                log.warn("Catch-up recebimento único userId={}: {}", usuarioId, e.getMessage());
            }
        }
        return toDto(cfg);
    }

    private RendaConfigDTO salvarFluxoDiario(Long usuarioId, RendaConfigRequest req) {
        RendaConfig cfg = rendaConfigRepository.findByUsuarioId(usuarioId).orElseGet(RendaConfig::new);
        if (cfg.getId() == null) {
            Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Utilizador não encontrado"));
            cfg.setUsuario(u);
        }
        cfg.setTipoConfiguracaoRenda(TipoConfiguracaoRenda.FLUXO_DIARIO);
        cfg.setValorRecebimentoUnico(null);
        cfg.setReceitaAutomaticaAtiva(false);
        cfg.setUltimoMesLancamentoAuto(null);
        if (req.getMetaFaturamentoMensal() != null) {
            cfg.setMetaFaturamentoMensal(req.getMetaFaturamentoMensal().setScale(2, RoundingMode.HALF_UP));
        }
        if (req.getDiaPagamento() != null) {
            cfg.setDiaPagamento(Math.max(1, Math.min(31, req.getDiaPagamento())));
        } else if (cfg.getDiaPagamento() == null) {
            cfg.setDiaPagamento(SalarioAutomaticoService.DIA_PAGAMENTO_PADRAO);
        }
        rendaConfigRepository.save(cfg);
        return toDto(cfg);
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
        TipoConfiguracaoRenda tipo = c.getTipoConfiguracaoRenda() != null
            ? c.getTipoConfiguracaoRenda()
            : TipoConfiguracaoRenda.CONTRACHEQUE;
        Long usuarioId = c.getUsuario() != null ? c.getUsuario().getId() : null;
        BigDecimal estimada = usuarioId != null ? calcularRendaMensal(c, usuarioId) : BigDecimal.ZERO;
        return RendaConfigDTO.builder()
            .salarioBruto(bruto)
            .descontosFixos(lista)
            .diaPagamento(c.getDiaPagamento())
            .salarioLiquido(c.getSalarioLiquido() != null ? c.getSalarioLiquido() : BigDecimal.ZERO)
            .totalDescontos(totalDesc.setScale(2, RoundingMode.HALF_UP))
            .percentualDescontosSobreBruto(pct)
            .receitaAutomaticaAtiva(c.isReceitaAutomaticaAtiva())
            .contaBancariaId(c.getContaBancaria() != null ? c.getContaBancaria().getId() : null)
            .contaBancariaNome(c.getContaBancaria() != null ? c.getContaBancaria().getNome() : null)
            .tipoConfiguracaoRenda(tipo)
            .valorRecebimentoUnico(c.getValorRecebimentoUnico())
            .metaFaturamentoMensal(c.getMetaFaturamentoMensal())
            .rendaMensalEstimada(estimada)
            .rotuloRenda(rotuloPorTipo(tipo))
            .build();
    }

}
