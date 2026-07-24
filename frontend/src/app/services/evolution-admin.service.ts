import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface EvolutionSessaoDetalhe {
  instancia: string;
  status: string;
  ativa: boolean;
  uptimeSegundos: number;
  memoriaEstimadaMb: number;
  mensagensEnviadas: number;
  mensagensRecebidas: number;
  mensagensEnviadasHoje: number;
  mensagensRecebidasHoje: number;
  desconexoesHoje: number;
  reconexoesHoje: number;
  falhasHoje: number;
  latenciaMediaMs: number;
  latenciaP95Ms: number;
  idadeUltimaAtividadeSegundos: number;
  instavel: boolean;
  motivoInstabilidade?: string | null;
}

export interface EvolutionHealth {
  sessoesAtivas: number;
  sessoesDesconectadas: number;
  mensagensHoje: number;
  reconexoesHoje: number;
  falhasHoje: number;
  latenciaMediaMs: number;
  latenciaP95Ms: number;
  coletadoEm?: string | null;
  sessoes?: EvolutionSessaoDetalhe[];
  sessoesInstaveis?: string[];
}

@Injectable({ providedIn: 'root' })
export class EvolutionAdminService {
  private readonly base = `${environment.apiUrl}/admin/evolution`;

  constructor(private http: HttpClient) {}

  obterHealth(detalhe = true): Observable<EvolutionHealth> {
    const params = new HttpParams().set('detalhe', detalhe ? 'true' : 'false');
    return this.http.get<EvolutionHealth>(`${this.base}/health`, { params });
  }
}
