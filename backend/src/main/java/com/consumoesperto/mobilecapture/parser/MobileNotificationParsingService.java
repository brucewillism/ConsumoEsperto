package com.consumoesperto.mobilecapture.parser;

import com.consumoesperto.mobilecapture.dto.MobileTransactionIngestionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MobileNotificationParsingService {

  private final List<BankNotificationParser> parsers;

  public Optional<ParsedMobileTransaction> parse(MobileTransactionIngestionRequest request) {
    if (request == null) {
      return Optional.empty();
    }
    String source = request.getSource() == null ? "" : request.getSource().trim().toUpperCase();
    if ("IOS_WALLET".equals(source) || "IOS_SHORTCUTS".equals(source)) {
      return parseIosStructured(request);
    }
    if ("ANDROID_NOTIFICATION".equals(source) || request.getPackageName() != null) {
      return parseAndroid(request);
    }
    if (request.getAmount() != null && request.getMerchant() != null) {
      return parseIosStructured(request);
    }
    return Optional.empty();
  }

  private Optional<ParsedMobileTransaction> parseIosStructured(MobileTransactionIngestionRequest request) {
    if (request.getAmount() == null || request.getAmount().signum() <= 0) {
      return Optional.empty();
    }
    String merchant = request.getMerchant();
    boolean confident = merchant != null && !merchant.isBlank();
    return Optional.of(ParsedMobileTransaction.builder()
        .amount(request.getAmount())
        .currency(defaultCurrency(request.getCurrency()))
        .merchant(MobileMoneyParser.normalizeMerchant(merchant))
        .merchantRaw(merchant)
        .parserName("IosWalletStructuredParser")
        .occurredAt(parseOccurredAt(request.getOccurredAt()))
        .confident(confident)
        .cardHint(request.getCardHint())
        .build());
  }

  private Optional<ParsedMobileTransaction> parseAndroid(MobileTransactionIngestionRequest request) {
    String pkg = request.getPackageName();
    for (BankNotificationParser parser : parsers) {
      if (parser instanceof GenericBankNotificationParser) {
        continue;
      }
      if (parser.supports(pkg) && parser.parse(request).isPresent()) {
        return parser.parse(request);
      }
    }
    return parsers.stream()
        .filter(p -> p instanceof GenericBankNotificationParser)
        .findFirst()
        .flatMap(p -> p.parse(request));
  }

  private static LocalDateTime parseOccurredAt(String raw) {
    if (raw == null || raw.isBlank()) {
      return LocalDateTime.now();
    }
    try {
      return LocalDateTime.parse(raw);
    } catch (DateTimeParseException e) {
      return LocalDateTime.now();
    }
  }

  private static String defaultCurrency(String currency) {
    return currency == null || currency.isBlank() ? "BRL" : currency;
  }
}
