import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type MobilePlatform = 'ANDROID_MACRODROID' | 'IOS_SHORTCUTS';

export interface MobileCaptureDevice {
  id: number;
  name: string;
  platform: MobilePlatform;
  active: boolean;
  createdAt?: string;
  lastSeenAt?: string;
  revokedAt?: string;
  lastTestOkAt?: string;
}

export interface MobileDeviceRegistration {
  deviceId: number;
  deviceToken: string;
  ingestionUrl: string;
  platform: MobilePlatform;
  name: string;
}

export interface MobileCaptureEventReview {
  id: number;
  source: string;
  status: string;
  amount?: number;
  currency?: string;
  merchantRaw?: string;
  merchantNormalized?: string;
  packageName?: string;
  cardHint?: string;
  parserName?: string;
  receivedAt?: string;
}

export interface ConfirmMobileCaptureEvent {
  merchant?: string;
  contaBancariaId?: number | null;
  cartaoCreditoId?: number | null;
  categoriaId?: number | null;
  saveMerchantCategoryRule?: boolean;
}

@Injectable({ providedIn: 'root' })
export class MobileCaptureService {
  private readonly base = `${environment.apiUrl}/mobile-capture`;

  constructor(private http: HttpClient) {}

  listDevices(): Observable<MobileCaptureDevice[]> {
    return this.http.get<MobileCaptureDevice[]>(`${this.base}/devices`);
  }

  registerDevice(name: string, platform: MobilePlatform): Observable<MobileDeviceRegistration> {
    return this.http.post<MobileDeviceRegistration>(`${this.base}/devices`, { name, platform });
  }

  revokeDevice(id: number): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.base}/devices/${id}/revoke`, {});
  }

  rotateToken(id: number): Observable<MobileDeviceRegistration> {
    return this.http.post<MobileDeviceRegistration>(`${this.base}/devices/${id}/rotate-token`, {});
  }

  listReviewEvents(): Observable<MobileCaptureEventReview[]> {
    return this.http.get<MobileCaptureEventReview[]>(`${this.base}/review/events`);
  }

  confirmEvent(id: number, body: ConfirmMobileCaptureEvent): Observable<unknown> {
    return this.http.post(`${this.base}/review/events/${id}/confirm`, body);
  }

  discardEvent(id: number): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.base}/review/events/${id}/discard`, {});
  }
}
