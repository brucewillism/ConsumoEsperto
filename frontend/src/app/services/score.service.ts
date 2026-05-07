import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface UsuarioScore {
  score: number;
  nivel: string;
  proximoNivelEm: number;
  dataAtualizacao?: string;
}

export interface HistoricoScore {
  id: number;
  delta: number;
  scoreResultante: number;
  motivo: string;
  detalhe?: string;
  dataEvento: string;
}

@Injectable({ providedIn: 'root' })
export class ScoreService {
  private readonly apiUrl = `${environment.apiUrl}/score`;

  constructor(private http: HttpClient) {}

  obter(): Observable<UsuarioScore> {
    return this.http.get<UsuarioScore>(this.apiUrl);
  }

  historico(): Observable<HistoricoScore[]> {
    return this.http.get<HistoricoScore[]>(`${this.apiUrl}/historico`);
  }
}
