import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AutonomyPreferencias {
  nivel: 'MANUAL' | 'ASSISTED' | 'AUTONOMOUS_SAFE' | string;
  registrarAuto: boolean;
  classificarAuto: boolean;
  aprenderCategorias: boolean;
  detectarAssinaturas: boolean;
  detectarDuplicatas: boolean;
  detectarAnomalias: boolean;
  preverSaldo: boolean;
  jarvisProativo: boolean;
  resumoDiario: boolean;
  resumoSemanal: boolean;
  silenciosoInicio?: string | null;
  silenciosoFim?: string | null;
  flags?: Record<string, boolean>;
}

export interface AutonomyReviewItem {
  id: number;
  kind: string;
  transacaoId?: number | null;
  title: string;
  detail?: string;
  confidence?: number;
  createdAt?: string;
}

export interface AutonomyResumo {
  enabled: boolean;
  revisoesPendentes?: number;
  transacoesAutoHoje?: number;
  itensConciliadosHoje?: number;
  anomaliasAbertas?: number;
  safeToSpend?: { safeToSpend?: number; disponivelAposObrigacoes?: number };
  forecast?: { d30?: { saldoProjetado?: number }; cashflowRisco?: boolean };
  faturas?: {
    cartaoNome?: string;
    valorConfirmado?: number;
    valorPendente?: number;
    valorProjetado?: number;
    diasAteFechamento?: number;
  }[];
}

@Injectable({ providedIn: 'root' })
export class AutonomyService {
  private readonly base = `${environment.apiUrl}/autonomy`;

  constructor(private http: HttpClient) {}

  resumo(): Observable<AutonomyResumo> {
    return this.http.get<AutonomyResumo>(`${this.base}/resumo`);
  }

  revisao(): Observable<{ total: number; itens: AutonomyReviewItem[] }> {
    return this.http.get<{ total: number; itens: AutonomyReviewItem[] }>(`${this.base}/revisao`);
  }

  resolver(id: number): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.base}/revisao/${id}/resolver`, {});
  }

  preferencias(): Observable<AutonomyPreferencias> {
    return this.http.get<AutonomyPreferencias>(`${this.base}/preferencias`);
  }

  salvarPreferencias(body: Partial<AutonomyPreferencias>): Observable<AutonomyPreferencias> {
    return this.http.put<AutonomyPreferencias>(`${this.base}/preferencias`, body);
  }
}
