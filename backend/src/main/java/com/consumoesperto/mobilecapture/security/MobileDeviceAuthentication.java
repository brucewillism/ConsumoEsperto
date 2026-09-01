package com.consumoesperto.mobilecapture.security;

import com.consumoesperto.model.MobileCaptureDevice;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

@Getter
public class MobileDeviceAuthentication extends AbstractAuthenticationToken {

  private final MobileCaptureDevice device;
  private final Long usuarioId;

  public MobileDeviceAuthentication(MobileCaptureDevice device) {
    super(List.of(new SimpleGrantedAuthority("ROLE_MOBILE_DEVICE")));
    this.device = device;
    this.usuarioId = device.getUsuario().getId();
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getPrincipal() {
    return device;
  }
}
