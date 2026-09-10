package com.consumoesperto.mobilecapture.security;

import com.consumoesperto.config.MobileCaptureProperties;
import com.consumoesperto.model.MobileCaptureDevice;
import com.consumoesperto.repository.MobileCaptureDeviceRepository;
import com.consumoesperto.security.ForwardedHttps;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MobileDeviceTokenFilter extends OncePerRequestFilter {

  public static final String DEVICE_TOKEN_HEADER = "X-CE-Device-Token";
  public static final String CLIENT_EVENT_HEADER = "X-CE-Client-Event-Id";

  private final MobileCaptureProperties properties;
  private final MobileCaptureDeviceRepository deviceRepository;
  private final ForwardedHttps forwardedHttps;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path == null || !path.startsWith("/api/ingestion/mobile/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    if (!properties.isEnabled()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    if (properties.isRequireHttps() && !ForwardedHttps.isHttps(request)) {
      forwardedHttps.rejectRequired(request, response);
      return;
    }
    String rawToken = request.getHeader(DEVICE_TOKEN_HEADER);
    if (rawToken == null || rawToken.isBlank()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    String hash = DeviceTokenHasher.hashToken(rawToken);
    Optional<MobileCaptureDevice> device = deviceRepository.findByTokenHashAndActiveTrue(hash);
    if (device.isEmpty() || !device.get().isUsable()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    SecurityContextHolder.getContext().setAuthentication(new MobileDeviceAuthentication(device.get()));
    filterChain.doFilter(request, response);
  }
}
