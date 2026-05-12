import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** OAuth incremental Google Calendar (read-only) — backend devolve URL para abrir no browser. */
@Injectable({ providedIn: 'root' })
export class GoogleCalendarLinkService {
  private readonly base = `${environment.apiUrl}/integracoes/google-calendar`;

  constructor(private http: HttpClient) {}

  iniciarVinculacao(): Observable<{ authorizationUrl: string }> {
    return this.http.get<{ authorizationUrl: string }>(`${this.base}/iniciar`);
  }
}
