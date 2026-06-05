import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ModoViagemJarvis {
  eventIdGoogle?: string;
  titulo?: string;
  dataEvento?: string;
  tetoSugerido?: number;
  mensagemResumo?: string;
}

@Injectable({ providedIn: 'root' })
export class ModoViagemJarvisService {
  private readonly baseUrl = `${environment.apiUrl}/jarvis/modo-viagem`;

  constructor(private http: HttpClient) {}

  listarPendentes(): Observable<ModoViagemJarvis[]> {
    return this.http.get<ModoViagemJarvis[]>(`${this.baseUrl}/pendentes`);
  }

  aceitar(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/aceitar`, {});
  }

  recusar(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/recusar`, {});
  }
}
