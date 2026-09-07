package com.consumoesperto.dto;

import com.consumoesperto.model.ImportedRowKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Linha de extrato já normalizada — ainda não é {@code Transacao}.
 */
public class ImportedFinancialRow {
    private int sourceLine;
    private LocalDate transactionDate;
    private String description;
    private String merchant;
    private BigDecimal amount;
    private String currency;
    private ImportedRowKind transactionType;
    private String accountHint;
    private String cardHint;
    private String externalId;
    private Integer installmentNumber;
    private Integer installmentTotal;
    private String rawReference;
    private String invalidReason;

    public int getSourceLine() { return sourceLine; }
    public void setSourceLine(int sourceLine) { this.sourceLine = sourceLine; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public ImportedRowKind getTransactionType() { return transactionType; }
    public void setTransactionType(ImportedRowKind transactionType) { this.transactionType = transactionType; }

    public String getAccountHint() { return accountHint; }
    public void setAccountHint(String accountHint) { this.accountHint = accountHint; }

    public String getCardHint() { return cardHint; }
    public void setCardHint(String cardHint) { this.cardHint = cardHint; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public Integer getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(Integer installmentNumber) { this.installmentNumber = installmentNumber; }

    public Integer getInstallmentTotal() { return installmentTotal; }
    public void setInstallmentTotal(Integer installmentTotal) { this.installmentTotal = installmentTotal; }

    public String getRawReference() { return rawReference; }
    public void setRawReference(String rawReference) { this.rawReference = rawReference; }

    public String getInvalidReason() { return invalidReason; }
    public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }

    public boolean isValid() {
        return invalidReason == null || invalidReason.isBlank();
    }
}
