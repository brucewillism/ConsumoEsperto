package com.consumoesperto.mobilecapture.parser;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MobileMoneyParser {

  private static final Pattern MONEY = Pattern.compile(
      "(?i)(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+,\\d{2})");

  private MobileMoneyParser() {
  }

  static Optional<BigDecimal> firstAmount(String... texts) {
    if (texts == null) {
      return Optional.empty();
    }
    for (String text : texts) {
      Optional<BigDecimal> parsed = parseAmount(text);
      if (parsed.isPresent()) {
        return parsed;
      }
    }
    return Optional.empty();
  }

  static Optional<BigDecimal> parseAmount(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    Matcher m = MONEY.matcher(text);
    if (!m.find()) {
      return Optional.empty();
    }
    String raw = m.group(1).replace(".", "").replace(",", ".");
    try {
      BigDecimal value = new BigDecimal(raw);
      if (value.compareTo(BigDecimal.ZERO) > 0) {
        return Optional.of(value);
      }
    } catch (NumberFormatException ignored) {
      // ignore
    }
    return Optional.empty();
  }

  static String normalizeMerchant(String merchant) {
    if (merchant == null) {
      return null;
    }
    String cleaned = merchant.trim().replaceAll("\\s+", " ");
    if (cleaned.length() > 200) {
      return cleaned.substring(0, 200);
    }
    return cleaned.toUpperCase(Locale.ROOT);
  }
}
