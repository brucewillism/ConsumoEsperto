package com.consumoesperto.mobilecapture.parser;

import com.consumoesperto.mobilecapture.dto.MobileTransactionIngestionRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GenericBankNotificationParser implements BankNotificationParser {

  private static final Pattern MERCHANT = Pattern.compile(
      "(?i)(?:em|no|na|para)\\s+(.+)$");

  @Override
  public boolean supports(String packageName) {
    return true;
  }

  @Override
  public String name() {
    return "GenericBankNotificationParser";
  }

  @Override
  public Optional<ParsedMobileTransaction> parse(MobileTransactionIngestionRequest request) {
    String text = join(
        request.getNotificationTitle(),
        request.getNotificationText(),
        request.getNotificationBigText());
    String norm = text.toLowerCase(Locale.ROOT);
    boolean purchaseHint = norm.contains("compra")
        || norm.contains("aprovad")
        || norm.contains("pix")
        || norm.contains("pagamento")
        || norm.contains("debito")
        || norm.contains("débito")
        || norm.contains("cartao")
        || norm.contains("cartão");
    if (!purchaseHint) {
      return Optional.empty();
    }
    Optional<java.math.BigDecimal> amount = MobileMoneyParser.firstAmount(text);
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

  private static String join(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (p != null && !p.isBlank()) {
        if (sb.length() > 0) {
          sb.append(' ');
        }
        sb.append(p.trim());
      }
    }
    return sb.toString();
  }

  private static String defaultCurrency(String currency) {
    return currency == null || currency.isBlank() ? "BRL" : currency;
  }
}
