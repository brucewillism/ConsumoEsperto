import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface MetaFinanceira {
  id: number;
  descricao: string;
  valorTotal: number;
  percentualComprometimento: number;
  valorPoupadoMensal: number;
  prazoMeses: number;
  rendaMediaReferencia: number | null;
  dataCriacao: string;
  /** 1 = baixa … 5 = máxima */
  prioridade: number;
  progressPercent: number;
  totalPercentualComprometidoMetas?: number | null;
  alertaComprometimento?: string | null;
}

export interface MetaFinanceiraListResponse {
  metas: MetaFinanceira[];
  totalPercentualComprometido: number;
  alertaComprometimento: string | null;
}

export interface MetaFinanceiraRequest {
  descricao: string;
  valorTotal: number;
  percentualComprometimento: number;
  prioridade?: number;
}

export interface RendaMediaResponse {
  rendaMensalMedia: number;
  calculadaDeLancamentos: boolean;
}

@Injectable({ providedIn: 'root' })
export class MetaFinanceiraService {
  private readonly base = `${environment.apiUrl}/metas`;

  constructor(private http: HttpClient) {}

  listar(): Observable<MetaFinanceiraListResponse> {
    return this.http.get<MetaFinanceiraListResponse>(this.base);
  }

  rendaMedia(): Observable<RendaMediaResponse> {
    return this.http.get<RendaMediaResponse>(`${this.base}/renda-media`);
  }

  criar(body: MetaFinanceiraRequest): Observable<MetaFinanceira> {
    return this.http.post<MetaFinanceira>(this.base, body);
  }

  atualizar(id: number, body: MetaFinanceiraRequest): Observable<MetaFinanceira> {
    return this.http.put<MetaFinanceira>(`${this.base}/${id}`, body);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
