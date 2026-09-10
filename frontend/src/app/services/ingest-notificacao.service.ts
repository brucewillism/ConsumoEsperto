import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface IngestTokenInfo {
  existe: boolean;
  ativo?: boolean;
  prefixo?: string;
  criadoEm?: string;
  ultimoUsoEm?: string;
}

export interface IngestPreferencias {
  ativo: boolean;
  agrupamento: 'IMEDIATO' | 'RESUMO';
  resumoMinutos: number;
  silenciosoInicio?: string | null;
  silenciosoFim?: string | null;
  contaPadraoId?: number | null;
}

export interface IngestFonte {
  id: number;
  app: string;
  canal: string;
  contaBancariaId?: number | null;
  cartaoCreditoId?: number | null;
}

export interface IngestConfig {
  ingestionUrl: string;
  token: IngestTokenInfo;
  preferencias: IngestPreferencias;
  fontes: IngestFonte[];
}

export interface IngestTokenGerado {
  token: string;
  ingestionUrl: string;
  aviso: string;
}

export interface IngestNotificacaoLog {
  id: number;
  origem: string;
  app: string;
  titulo?: string;
  texto: string;
  status: string;
  erro?: string;
  transacaoId?: number;
  recebidoEm?: string;
  criadoEm?: string;
}

@Injectable({ providedIn: 'root' })
export class IngestNotificacaoService {
  private readonly base = `${environment.apiUrl}/ingest/config`;

  constructor(private http: HttpClient) {}

  obter(): Observable<IngestConfig> {
    return this.http.get<IngestConfig>(this.base);
  }

  gerarToken(): Observable<IngestTokenGerado> {
    return this.http.post<IngestTokenGerado>(`${this.base}/token`, {});
  }

  revogarToken(): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.base}/token/revogar`, {});
  }

  salvarPreferencias(body: Partial<IngestPreferencias>): Observable<IngestPreferencias> {
    return this.http.put<IngestPreferencias>(`${this.base}/preferencias`, body);
  }

  upsertFonte(body: {
    app: string;
    canal: string;
    contaBancariaId?: number | null;
    cartaoCreditoId?: number | null;
  }): Observable<IngestFonte> {
    return this.http.post<IngestFonte>(`${this.base}/fontes`, body);
  }

  removerFonte(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/fontes/${id}`);
  }

  listarNotificacoes(limit = 50): Observable<IngestNotificacaoLog[]> {
    return this.http.get<IngestNotificacaoLog[]>(`${this.base}/notificacoes`, {
      params: { limit: String(limit) },
    });
  }
}
