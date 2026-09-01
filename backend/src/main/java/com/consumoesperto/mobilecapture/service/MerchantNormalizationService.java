package com.consumoesperto.mobilecapture.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MerchantNormalizationService {

  private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9\\s]");

  public String normalize(String merchantRaw) {
    if (merchantRaw == null || merchantRaw.isBlank()) {
      return null;
    }
    String upper = Normalizer.normalize(merchantRaw, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toUpperCase(Locale.ROOT)
        .trim()
        .replaceAll("\\s+", " ");
    upper = NON_ALNUM.matcher(upper).replaceAll(" ").replaceAll("\\s+", " ").trim();
    if (upper.contains("*")) {
      upper = upper.replace("*", " ").replaceAll("\\s+", " ").trim();
    }
    return upper.length() > 200 ? upper.substring(0, 200) : upper;
  }
}
