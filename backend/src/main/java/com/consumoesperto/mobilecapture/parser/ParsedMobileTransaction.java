package com.consumoesperto.mobilecapture.parser;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class ParsedMobileTransaction {
  BigDecimal amount;
  String currency;
  String merchant;
  String merchantRaw;
  String parserName;
  LocalDateTime occurredAt;
  boolean confident;
  String cardHint;
}
