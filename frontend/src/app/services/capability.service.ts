import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CapabilityInvokeResponse {
  capability: string;
  outcome: string;
  execution_path: string;
  data: Record<string, unknown>;
  trace_id?: string;
}

@Injectable({ providedIn: 'root' })
export class CapabilityService {
  private readonly apiUrl = `${environment.apiUrl}/capabilities`;

  constructor(private http: HttpClient) {}

  invoke(capability: string, input: Record<string, unknown> = {}): Observable<CapabilityInvokeResponse> {
    return this.http.post<CapabilityInvokeResponse>(
      `${this.apiUrl}/${encodeURIComponent(capability)}:invoke`,
      { input }
    );
  }
}
