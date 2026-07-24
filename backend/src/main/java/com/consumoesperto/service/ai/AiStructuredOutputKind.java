package com.consumoesperto.service.ai;

import com.consumoesperto.dto.ai.structured.BankAccountStructuredDTO;
import com.consumoesperto.dto.ai.structured.BudgetStructuredDTO;
import com.consumoesperto.dto.ai.structured.CartaoStructuredDTO;
import com.consumoesperto.dto.ai.structured.ConsignedLoanStructuredDTO;
import com.consumoesperto.dto.ai.structured.ContrachequeStructuredDTO;
import com.consumoesperto.dto.ai.structured.EntityMutationStructuredDTO;
import com.consumoesperto.dto.ai.structured.FixedObligationStructuredDTO;
import com.consumoesperto.dto.ai.structured.InstallmentPurchaseStructuredDTO;
import com.consumoesperto.dto.ai.structured.MetaStructuredDTO;
import com.consumoesperto.dto.ai.structured.OcrComprovanteStructuredDTO;
import com.consumoesperto.dto.ai.structured.OcrCupomStructuredDTO;
import com.consumoesperto.dto.ai.structured.OcrFaturaStructuredDTO;
import com.consumoesperto.dto.ai.structured.SettleDebtStructuredDTO;
import com.consumoesperto.dto.ai.structured.SplitBillStructuredDTO;
import com.consumoesperto.dto.ai.structured.TransferBetweenAccountsStructuredDTO;
import com.consumoesperto.dto.ai.structured.WhatsappDespesaStructuredDTO;
import com.consumoesperto.dto.ai.structured.WhatsappReceitaStructuredDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum AiStructuredOutputKind {
    OCR_CUPOM(OcrCupomStructuredDTO.class),
    OCR_COMPROVANTE(OcrComprovanteStructuredDTO.class),
    OCR_FATURA(OcrFaturaStructuredDTO.class),
    CONTRACHEQUE(ContrachequeStructuredDTO.class),
    WHATSAPP_DESPESA(WhatsappDespesaStructuredDTO.class),
    WHATSAPP_RECEITA(WhatsappReceitaStructuredDTO.class),
    CARTAO(CartaoStructuredDTO.class),
    META(MetaStructuredDTO.class),
    TRANSFER_BETWEEN_ACCOUNTS(TransferBetweenAccountsStructuredDTO.class),
    CONSIGNED_LOAN(ConsignedLoanStructuredDTO.class),
    INSTALLMENT_PURCHASE(InstallmentPurchaseStructuredDTO.class),
    FIXED_OBLIGATION(FixedObligationStructuredDTO.class),
    BUDGET(BudgetStructuredDTO.class),
    BANK_ACCOUNT(BankAccountStructuredDTO.class),
    SETTLE_DEBT(SettleDebtStructuredDTO.class),
    SPLIT_BILL(SplitBillStructuredDTO.class),
    ENTITY_MUTATION(EntityMutationStructuredDTO.class);

    private final Class<?> dtoClass;

    public static Optional<AiStructuredOutputKind> resolveWhatsappMutation(JsonNode cmd) {
        if (cmd == null || cmd.isNull()) {
            return Optional.empty();
        }
        String action = cmd.path("action").asText("").trim();
        if (!isFinancialMutationAction(action)) {
            return Optional.empty();
        }
        if ("CREATE_EXPENSE".equals(action) && readInstallmentCount(cmd) >= 2) {
            return Optional.of(INSTALLMENT_PURCHASE);
        }
        return Optional.of(switch (action) {
            case "CREATE_EXPENSE" -> WHATSAPP_DESPESA;
            case "CREATE_INCOME" -> WHATSAPP_RECEITA;
            case "CREATE_CARD" -> CARTAO;
            case "CREATE_META" -> META;
            case "CREATE_BANK_ACCOUNT" -> BANK_ACCOUNT;
            case "CREATE_BUDGET" -> BUDGET;
            case "CREATE_FIXED_EXPENSE", "CREATE_SUBSCRIPTION" -> FIXED_OBLIGATION;
            case "TRANSFER_BETWEEN_ACCOUNTS" -> TRANSFER_BETWEEN_ACCOUNTS;
            case "RECORD_CONSIGNMENT_LOAN" -> CONSIGNED_LOAN;
            case "SETTLE_DEBT" -> SETTLE_DEBT;
            case "SPLIT_BILL" -> SPLIT_BILL;
            case "UPDATE_ENTITY_CONFIG", "UPDATE_ACCOUNT_CONFIG", "MANAGE_ENTITY", "CONFIRM_FISCAL_PROVISION" ->
                ENTITY_MUTATION;
            default -> throw new IllegalStateException("ação mutável sem kind: " + action);
        });
    }

    public static boolean isFinancialMutationAction(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        return switch (action) {
            case "CREATE_EXPENSE", "CREATE_INCOME", "CREATE_CARD", "CREATE_META",
                 "CREATE_BANK_ACCOUNT", "CREATE_BUDGET", "CREATE_FIXED_EXPENSE", "CREATE_SUBSCRIPTION",
                 "TRANSFER_BETWEEN_ACCOUNTS", "RECORD_CONSIGNMENT_LOAN", "SETTLE_DEBT", "SPLIT_BILL",
                 "UPDATE_ENTITY_CONFIG", "UPDATE_ACCOUNT_CONFIG", "MANAGE_ENTITY", "CONFIRM_FISCAL_PROVISION" -> true;
            default -> false;
        };
    }

    private static int readInstallmentCount(JsonNode cmd) {
        int n = cmd.path("installmentCount").asInt(0);
        if (n <= 0) {
            n = cmd.path("installments").asInt(0);
        }
        return n;
    }
}
