package com.consumoesperto.mobilecapture.parser;

import com.consumoesperto.mobilecapture.dto.MobileTransactionIngestionRequest;

import java.util.Optional;

public interface BankNotificationParser {

  boolean supports(String packageName);

  String name();

  Optional<ParsedMobileTransaction> parse(MobileTransactionIngestionRequest request);
}
