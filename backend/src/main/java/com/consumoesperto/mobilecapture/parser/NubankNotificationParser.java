package com.consumoesperto.mobilecapture.parser;

import com.consumoesperto.mobilecapture.dto.MobileTransactionIngestionRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NubankNotificationParser implements BankNotificationParser {

  private static final Pattern MERCHANT = Pattern.compile(
      "(?i)(?:em|no|na)\\s+(.+)$");

  @Override
  public boolean supports(String packageName) {
    return packageName != null && packageName.toLowerCase().contains("nu");
  }

  @Override
  public String name() {
    return "NubankNotificationParser";
  }

  @Override
  public Optional<ParsedMobileTransaction> parse(MobileTransactionIngestionRequest request) {
    String text = join(request.getNotificationText(), request.getNotificationBigText());
    Optional<java.math.BigDecimal> amount = MobileMoneyParser.firstAmount(
        text, request.getNotificationTitle());
    if (amount.isEmpty()) {
      return Optional.empty();
    }
    String merchant = request.getMerchant();
    if (merchant == null || merchant.isBlank()) {
      Matcher m = MERCHANT.matcher(text);
      if (m.find()) {
        merchant = m.group(1).trim();
      }
    }
    boolean confident = merchant != null && !merchant.isBlank();
    return Optional.of(ParsedMobileTransaction.builder()
        .amount(amount.get())
        .currency(defaultCurrency(request.getCurrency()))
        .merchant(MobileMoneyParser.normalizeMerchant(merchant))
        .merchantRaw(merchant)
        .parserName(name())
        .confident(confident)
        .cardHint(request.getCardHint())
        .build());
  }

  private static String join(String a, String b) {
    if (a == null) {
      return b == null ? "" : b;
    }
    if (b == null) {
      return a;
    }
    return a + " " + b;
  }

  private static String defaultCurrency(String currency) {
    return currency == null || currency.isBlank() ? "BRL" : currency;
  }
}
