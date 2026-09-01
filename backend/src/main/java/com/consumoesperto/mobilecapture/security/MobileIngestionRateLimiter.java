package com.consumoesperto.mobilecapture.security;

import com.consumoesperto.config.MobileCaptureProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MobileIngestionRateLimiter {

  private final MobileCaptureProperties properties;
  private final Map<Long, Window> windows = new ConcurrentHashMap<>();

  public MobileIngestionRateLimiter(MobileCaptureProperties properties) {
    this.properties = properties;
  }

  public void checkOrThrow(Long deviceId) {
    if (deviceId == null) {
      return;
    }
    long now = System.currentTimeMillis();
    Window window = windows.computeIfAbsent(deviceId, id -> new Window(now));
    synchronized (window) {
      if (now - window.startMs > 60_000L) {
        window.startMs = now;
        window.count.set(0);
      }
      if (window.count.incrementAndGet() > properties.getRateLimitPerMinute()) {
        throw new MobileCaptureException("Limite de requisições do dispositivo excedido");
      }
    }
  }

  private static final class Window {
    private long startMs;
    private final AtomicInteger count = new AtomicInteger();

    private Window(long startMs) {
      this.startMs = startMs;
    }
  }
}
