package com.consumoesperto.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiStructuredOutputService {

    private static final int MAX_RETRIES = 2;

    private final Validator validator;
    private final AiStructuredOutputMetrics metrics;
    private final ObjectMapper strictMapper = buildStrictMapper();

    public <T> AiStructuredOutputResult<T> parseAndValidate(
        JsonNode raw,
        AiStructuredOutputKind kind,
        Class<T> dtoClass,
        Long userId,
        IntFunction<JsonNode> retrySupplier
    ) {
        JsonNode current = raw;
        int attempts = 1;
        boolean corrected = false;
        List<String> lastErrors = List.of();

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0 && retrySupplier != null) {
                try {
                    JsonNode retryNode = retrySupplier.apply(attempt);
                    if (retryNode != null && !retryNode.isNull()) {
                        current = retryNode;
                        attempts++;
                    }
                } catch (Exception e) {
                    log.warn("[AI-STRUCT] retry falhou kind={} userId={} attempt={}: {}",
                        kind, userId, attempt, e.getMessage());
                }
            }

            ObjectNode normalized = normalizeNode(current, kind);
            ParseAttempt<T> parsed = tryDeserialize(normalized, dtoClass);
            if (parsed.corrected()) {
                corrected = true;
            }
            if (parsed.dto() == null) {
                lastErrors = parsed.errors();
                continue;
            }

            List<String> validationErrors = validateBean(parsed.dto());
            if (validationErrors.isEmpty()) {
                postValidate(kind, parsed.dto(), validationErrors);
            }
            if (validationErrors.isEmpty()) {
                AiStructuredOutputStatus status = corrected
                    ? AiStructuredOutputStatus.CORRECTED
                    : AiStructuredOutputStatus.VALID;
                metrics.record(status);
                logStructured(kind, userId, status, attempts, null);
                return AiStructuredOutputResult.<T>builder()
                    .status(status)
                    .payload(parsed.dto())
                    .errors(List.of())
                    .rawJson(toJson(normalized))
                    .attempts(attempts)
                    .requiresUserConfirmation(false)
                    .build();
            }
            lastErrors = validationErrors;
        }

        metrics.record(AiStructuredOutputStatus.REJECTED);
        logStructured(kind, userId, AiStructuredOutputStatus.REJECTED, attempts, lastErrors);
        return AiStructuredOutputResult.<T>builder()
            .status(AiStructuredOutputStatus.NEEDS_CONFIRMATION)
            .payload(null)
            .errors(lastErrors)
            .rawJson(toJson(current))
            .attempts(attempts)
            .requiresUserConfirmation(true)
            .build();
    }

    /**
     * Gate obrigatório antes de qualquer mutação financeira via WhatsApp.
     * Falha → NEEDS_CONFIRMATION; nunca executa handler com JSON inválido.
     */
    public Optional<AiStructuredOutputResult<?>> validateWhatsappMutation(
        JsonNode cmd,
        Long userId,
        IntFunction<JsonNode> retrySupplier
    ) {
        Optional<AiStructuredOutputKind> kindOpt = AiStructuredOutputKind.resolveWhatsappMutation(cmd);
        if (kindOpt.isEmpty()) {
            return Optional.empty();
        }
        AiStructuredOutputKind kind = kindOpt.get();
        AiStructuredOutputResult<?> result = parseAndValidate(cmd, kind, dtoClassFor(kind), userId, retrySupplier);
        if (result.isValid()) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> dtoClassFor(AiStructuredOutputKind kind) {
        return (Class<T>) kind.getDtoClass();
    }

    public AiStructuredOutputResult<JsonNode> gateWhatsappCommand(
        JsonNode cmd,
        Long userId,
        IntFunction<JsonNode> retrySupplier
    ) {
        Optional<AiStructuredOutputKind> kindOpt = AiStructuredOutputKind.resolveWhatsappMutation(cmd);
        if (kindOpt.isEmpty()) {
            return AiStructuredOutputResult.<JsonNode>builder()
                .status(AiStructuredOutputStatus.VALID)
                .payload(cmd)
                .errors(List.of())
                .rawJson(toJson(cmd))
                .attempts(1)
                .requiresUserConfirmation(false)
                .build();
        }
        return castNodeResult(parseAndValidate(cmd, kindOpt.get(), dtoClassFor(kindOpt.get()), userId, retrySupplier));
    }

    public boolean isFinancialMutationAction(String action) {
        return AiStructuredOutputKind.isFinancialMutationAction(action);
    }

    public String mensagemConfirmacaoUsuario(AiStructuredOutputKind kind, List<String> errors) {
        String detalhes = errors == null || errors.isEmpty()
            ? "formato inválido"
            : String.join("; ", errors);
        return "Não consegui validar os dados da IA (" + kind.name() + "). "
            + "Detalhes: " + detalhes + ". "
            + "Confirme manualmente reenviando com valor, descrição e demais campos explícitos.";
    }

    private <T> AiStructuredOutputResult<JsonNode> castNodeResult(AiStructuredOutputResult<T> inner) {
        JsonNode node = inner.isValid()
            ? strictMapper.valueToTree(inner.getPayload())
            : null;
        return AiStructuredOutputResult.<JsonNode>builder()
            .status(inner.getStatus())
            .payload(node)
            .errors(inner.getErrors())
            .rawJson(inner.getRawJson())
            .attempts(inner.getAttempts())
            .requiresUserConfirmation(inner.isRequiresUserConfirmation())
            .build();
    }

    private <T> void postValidate(AiStructuredOutputKind kind, T dto, List<String> errors) {
        if (kind == AiStructuredOutputKind.OCR_CUPOM && dto instanceof com.consumoesperto.dto.ai.structured.OcrCupomStructuredDTO cupom) {
            if (cupom.getErro() != null && !cupom.getErro().isBlank()) {
                errors.add("OCR reportou erro: " + cupom.getErro());
            }
            if (cupom.getConfianca() != null && cupom.getConfianca() < 0.45d) {
                errors.add("confianca abaixo do mínimo seguro (0.45)");
            }
        }
        if (kind == AiStructuredOutputKind.OCR_COMPROVANTE && dto instanceof com.consumoesperto.dto.ai.structured.OcrComprovanteStructuredDTO comp) {
            if (comp.getConfianca() != null && comp.getConfianca() < 0.45d) {
                errors.add("confianca abaixo do mínimo seguro (0.45)");
            }
        }
        if (kind == AiStructuredOutputKind.CONTRACHEQUE && dto instanceof com.consumoesperto.dto.ai.structured.ContrachequeStructuredDTO cc) {
            if (!"CONTRACHEQUE".equalsIgnoreCase(cc.getTipoDocumento())) {
                errors.add("tipoDocumento deve ser CONTRACHEQUE");
            }
        }
        if (kind == AiStructuredOutputKind.OCR_FATURA && dto instanceof com.consumoesperto.dto.ai.structured.OcrFaturaStructuredDTO fat) {
            if (!"FATURA_CARTAO".equalsIgnoreCase(fat.getTipoDocumento())) {
                errors.add("tipoDocumento deve ser FATURA_CARTAO");
            }
        }
        if (kind == AiStructuredOutputKind.INSTALLMENT_PURCHASE
            && dto instanceof com.consumoesperto.dto.ai.structured.InstallmentPurchaseStructuredDTO inst) {
            boolean temCartao = inst.getCardName() != null && !inst.getCardName().isBlank();
            boolean temBanco = inst.getBank() != null && !inst.getBank().isBlank();
            if (!temCartao && !temBanco) {
                errors.add("parcelamento exige cardName ou bank");
            }
        }
        if (kind == AiStructuredOutputKind.ENTITY_MUTATION
            && dto instanceof com.consumoesperto.dto.ai.structured.EntityMutationStructuredDTO em) {
            boolean temRef = isNotBlank(em.getIdentifier()) || isNotBlank(em.getSearchPhrase())
                || isNotBlank(em.getCardName());
            if ("MANAGE_ENTITY".equals(em.getAction()) && !temRef) {
                errors.add("MANAGE_ENTITY exige identifier ou searchPhrase");
            }
            if ("UPDATE_ENTITY_CONFIG".equals(em.getAction()) && !temRef) {
                errors.add("UPDATE_ENTITY_CONFIG exige identifier ou searchPhrase");
            }
            if ("UPDATE_ACCOUNT_CONFIG".equals(em.getAction()) && !isNotBlank(em.getCardName()) && !temRef) {
                errors.add("UPDATE_ACCOUNT_CONFIG exige cardName ou identifier");
            }
        }
        if (kind == AiStructuredOutputKind.FIXED_OBLIGATION
            && dto instanceof com.consumoesperto.dto.ai.structured.FixedObligationStructuredDTO fix) {
            if ("CREATE_SUBSCRIPTION".equals(fix.getAction())
                && (fix.getAmount() == null || fix.getAmount().compareTo(BigDecimal.ZERO) <= 0)) {
                errors.add("CREATE_SUBSCRIPTION exige amount válido");
            }
        }
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private ObjectNode normalizeNode(JsonNode raw, AiStructuredOutputKind kind) {
        ObjectNode node = raw != null && raw.isObject()
            ? ((ObjectNode) raw).deepCopy()
            : strictMapper.createObjectNode();

        applyAliases(node, kind);
        trimStringFields(node);
        coerceMoneyFields(node,
            "amount", "valor", "valorTotal", "salarioBruto", "salarioLiquido", "creditLimit",
            "valorTomado", "budgetLimit", "initialBalance", "installmentAmount");
        normalizeTransferAccounts(node, kind);
        normalizeSplitMembers(node, kind);
        normalizeAction(node, kind);
        normalizeTipoComprovante(node);
        normalizeTipoDocumento(node);
        ensureConfianca(node);
        if (kind == AiStructuredOutputKind.META && !node.has("description") && node.has("searchPhrase")) {
            node.put("description", node.get("searchPhrase").asText("").trim());
        }
        if (kind == AiStructuredOutputKind.META && !node.has("description") && node.has("identifier")) {
            node.put("description", node.get("identifier").asText("").trim());
        }
        return node;
    }

    private void applyAliases(ObjectNode node, AiStructuredOutputKind kind) {
        copyAliasIfMissing(node, "valor", "amount");
        if (kind != AiStructuredOutputKind.OCR_CUPOM) {
            copyAliasIfMissing(node, "valorTotal", "amount");
        }
        copyAliasIfMissing(node, "descricao", "description");
        copyAliasIfMissing(node, "descricaoItem", "description");
        copyAliasIfMissing(node, "nome", "description");
        copyAliasIfMissing(node, "beneficiario", "descricao");
        copyAliasIfMissing(node, "pagador", "descricao");
        copyAliasIfMissing(node, "loja", "estabelecimento");
        copyAliasIfMissing(node, "merchant", "estabelecimento");
        copyAliasIfMissing(node, "banco", kind == AiStructuredOutputKind.CARTAO ? "bank" : "bancoCartao");
        copyAliasIfMissing(node, "cartao", "bancoCartao");
        copyAliasIfMissing(node, "categoriaNome", "categoria");
        copyAliasIfMissing(node, "categoriaNome", "categoryName");
        if (kind == AiStructuredOutputKind.BUDGET) {
            copyAliasIfMissing(node, "amount", "budgetLimit");
        }
        copyAliasIfMissing(node, "valorTomado", "valorTotal");
        copyAliasIfMissing(node, "accountName", "identifier");
        if (kind == AiStructuredOutputKind.BUDGET && !node.has("categoryName") && node.has("description")) {
            copyAliasIfMissing(node, "description", "categoryName");
        }
        if (kind == AiStructuredOutputKind.FIXED_OBLIGATION && !node.has("description") && node.has("searchPhrase")) {
            copyAliasIfMissing(node, "searchPhrase", "description");
        }
    }

    private void normalizeTransferAccounts(ObjectNode node, AiStructuredOutputKind kind) {
        if (kind != AiStructuredOutputKind.TRANSFER_BETWEEN_ACCOUNTS) {
            return;
        }
        copyAliasIfMissing(node, "accountOrigin", "contaOrigem");
        copyAliasIfMissing(node, "accountDestination", "contaDestino");
    }

    private void normalizeSplitMembers(ObjectNode node, AiStructuredOutputKind kind) {
        if (kind != AiStructuredOutputKind.SPLIT_BILL) {
            return;
        }
        if (node.has("splitMembers") && node.get("splitMembers").isArray()) {
            return;
        }
        JsonNode alt = node.get("membros");
        if (alt != null && alt.isArray()) {
            node.set("splitMembers", alt);
        }
    }

    private void copyAliasIfMissing(ObjectNode node, String from, String to) {
        if (!node.has(to) && node.has(from)) {
            node.set(to, node.get(from));
        }
    }
    private void trimStringFields(ObjectNode node) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> f = fields.next();
            if (f.getValue().isTextual()) {
                node.put(f.getKey(), f.getValue().asText("").trim());
            }
        }
    }

    private void coerceMoneyFields(ObjectNode node, String... fields) {
        for (String field : fields) {
            if (!node.has(field)) {
                continue;
            }
            JsonNode v = node.get(field);
            if (v.isNumber()) {
                node.put(field, BigDecimal.valueOf(v.asDouble()).setScale(2, RoundingMode.HALF_UP));
            } else if (v.isTextual()) {
                BigDecimal parsed = parseMoneyBr(v.asText(""));
                if (parsed != null) {
                    node.put(field, parsed);
                }
            }
        }
    }

    private void normalizeAction(ObjectNode node, AiStructuredOutputKind kind) {
        if (node.has("action")) {
            return;
        }
        String action = switch (kind) {
            case WHATSAPP_DESPESA -> "CREATE_EXPENSE";
            case WHATSAPP_RECEITA -> "CREATE_INCOME";
            case CARTAO -> "CREATE_CARD";
            case META -> "CREATE_META";
            case TRANSFER_BETWEEN_ACCOUNTS -> "TRANSFER_BETWEEN_ACCOUNTS";
            case CONSIGNED_LOAN -> "RECORD_CONSIGNMENT_LOAN";
            case INSTALLMENT_PURCHASE -> "CREATE_EXPENSE";
            case FIXED_OBLIGATION -> null;
            case BUDGET -> "CREATE_BUDGET";
            case BANK_ACCOUNT -> "CREATE_BANK_ACCOUNT";
            case SETTLE_DEBT -> "SETTLE_DEBT";
            case SPLIT_BILL -> "SPLIT_BILL";
            case ENTITY_MUTATION -> null;
            default -> null;
        };
        if (action != null) {
            node.put("action", action);
        }
    }

    private void normalizeTipoComprovante(ObjectNode node) {
        if (node.has("tipo") && node.get("tipo").isTextual()) {
            node.put("tipo", node.get("tipo").asText("").trim().toUpperCase(Locale.ROOT));
        }
    }

    private void normalizeTipoDocumento(ObjectNode node) {
        if (node.has("tipoDocumento") && node.get("tipoDocumento").isTextual()) {
            node.put("tipoDocumento", node.get("tipoDocumento").asText("").trim().toUpperCase(Locale.ROOT));
        }
    }

    private void ensureConfianca(ObjectNode node) {
        if (!node.has("confianca")) {
            node.put("confianca", 0.5d);
        }
    }

    private <T> ParseAttempt<T> tryDeserialize(ObjectNode node, Class<T> dtoClass) {
        List<String> errors = new ArrayList<>();
        boolean corrected = false;
        try {
            T dto = strictMapper.treeToValue(node, dtoClass);
            return new ParseAttempt<>(dto, errors, corrected);
        } catch (Exception first) {
            errors.add("deserialização: " + rootMessage(first));
            try {
                strictMapper.readerFor(dtoClass)
                    .with(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                    .readValue(node);
                T dto = strictMapper.treeToValue(node, dtoClass);
                corrected = true;
                return new ParseAttempt<>(dto, List.of(), true);
            } catch (Exception second) {
                errors.add("retry parse: " + rootMessage(second));
                return new ParseAttempt<>(null, errors, corrected);
            }
        }
    }

    private <T> List<String> validateBean(T dto) {
        List<String> errors = new ArrayList<>();
        for (ConstraintViolation<T> v : validator.validate(dto)) {
            errors.add(v.getPropertyPath() + ": " + v.getMessage());
        }
        return errors;
    }

    private static BigDecimal parseMoneyBr(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String t = raw.replace("R$", "").trim();
            if (t.contains(",")) {
                t = t.replace(".", "").replace(",", ".");
            }
            return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static ObjectMapper buildStrictMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
        mapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, true);
        return mapper;
    }

    private String toJson(JsonNode node) {
        try {
            return strictMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return String.valueOf(node);
        }
    }

    private void logStructured(
        AiStructuredOutputKind kind,
        Long userId,
        AiStructuredOutputStatus status,
        int attempts,
        List<String> errors
    ) {
        log.info("[AI-STRUCT] kind={} status={} userId={} attempts={} errors={}",
            kind, status, userId, attempts, errors == null ? List.of() : errors);
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }

    private record ParseAttempt<T>(T dto, List<String> errors, boolean corrected) {}
}
