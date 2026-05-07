import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Orcamento {
  id: number;
  categoriaId: number;
  categoriaNome: string;
  valorLimite: number;
  mes: number;
  ano: number;
  valorGasto: number;
  percentualUso: number;
  status: 'VERDE' | 'AMARELO' | 'VERMELHO';
  compartilhado?: boolean;
  grupoFamiliarId?: number | null;
  membrosContabilizados?: number;
}

export interface OrcamentoRequest {
  categoriaId: number;
  valorLimite: number;
  mes?: number;
  ano?: number;
  compartilhado?: boolean;
}

export interface ForecastFinanceiro {
  diaAtual: number;
  diasNoMes: number;
  rendaLiquida: number;
  gastoAtual: number;
  mediaDiaria: number;
  gastoProjetado: number;
  saldoProjetado: number;
  probabilidadeVermelho: number;
  nivelRisco: string;
  mensagemIa: string;
  maioresCategorias: string[];
}

@Injectable({ providedIn: 'root' })
export class OrcamentoService {
  private readonly base = `${environment.apiUrl}/orcamentos`;

  constructor(private http: HttpClient) {}

  listar(mes?: number, ano?: number): Observable<Orcamento[]> {
    const params: Record<string, string> = {};
    if (mes) params['mes'] = String(mes);
    if (ano) params['ano'] = String(ano);
    return this.http.get<Orcamento[]>(this.base, { params });
  }

  salvar(body: OrcamentoRequest): Observable<Orcamento> {
    return this.http.post<Orcamento>(this.base, body);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  forecast(): Observable<ForecastFinanceiro> {
    return this.http.get<ForecastFinanceiro>(`${this.base}/forecast`);
  }
}
