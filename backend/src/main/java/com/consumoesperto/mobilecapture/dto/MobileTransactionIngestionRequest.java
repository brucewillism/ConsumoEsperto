package com.consumoesperto.mobilecapture.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MobileTransactionIngestionRequest {

  private String source;

  @JsonProperty("client_event_id")
  private String clientEventId;

  @JsonProperty("occurred_at")
  private String occurredAt;

  private BigDecimal amount;

  private String currency;

  private String merchant;

  @JsonProperty("card_hint")
  private String cardHint;

  @JsonProperty("package")
  private String packageName;

  @JsonProperty("notification_title")
  private String notificationTitle;

  @JsonProperty("notification_text")
  private String notificationText;

  @JsonProperty("notification_big_text")
  private String notificationBigText;
}
