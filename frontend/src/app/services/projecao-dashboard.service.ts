import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SimulacaoImpacto {
  id: string;
  descricao: string;
  valorMensalImpacto: number;
  mesesImpacto: number;
  metaDescricao?: string;
  icone?: string;
  ativa: boolean;
  mensagem?: string;
  impactoScore?: number;
  criadaEm?: string;
}

export interface TimelineImpacto {
  titulo: string;
  icone: string;
  mesesOriginais: number;
  mesesProjetados: number;
  deslocamentoMeses: number;
}

export interface DashboardProjection {
  labels: string[];
  real: Array<number | null>;
  projetado: number[];
  simulado: number[];
  timelineImpacto: TimelineImpacto[];
  simulacoesAtivas: SimulacaoImpacto[];
}

export interface OportunidadeInvestimento {
  saldoOcioso: number;
  rendimentoPoupanca: number;
  rendimentoTesouroSelic: number;
  rendimentoCdb: number;
  melhorOpcao: string;
  explicacaoIa: string;
}

@Injectable({ providedIn: 'root' })
export class ProjecaoDashboardService {
  private readonly base = `${environment.apiUrl}/projecoes`;

  constructor(private http: HttpClient) {}

  dashboard(): Observable<DashboardProjection> {
    return this.http.get<DashboardProjection>(`${this.base}/dashboard`);
  }

  definirSimulacoesAtivas(ativa: boolean): Observable<SimulacaoImpacto[]> {
    return this.http.patch<SimulacaoImpacto[]>(`${this.base}/simulacoes/ativas`, { ativa });
  }

  oportunidadeInvestimento(): Observable<OportunidadeInvestimento> {
    return this.http.get<OportunidadeInvestimento>(`${this.base}/oportunidade-investimento`);
  }
}
