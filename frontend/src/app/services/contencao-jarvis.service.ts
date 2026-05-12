import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SugestaoContencaoJarvis {
  id?: number;
  importacaoFaturaCartaoId?: number;
  categoriaId?: number;
  categoriaNome?: string;
  chaveAgrupamento?: string;
  rotuloExibicao?: string;
  tipoHabito?: string;
  valorGastoReferencia?: number;
  mediaTresMeses?: number;
  percentualAumento?: number;
  valorTetoSugerido?: number;
  mesAlvo?: number;
  anoAlvo?: number;
  status?: string;
  mensagemResumo?: string;
}

@Injectable({ providedIn: 'root' })
export class ContencaoJarvisService {
  private readonly baseUrl = `${environment.apiUrl}/jarvis/sugestoes-contencao`;

  constructor(private http: HttpClient) {}

  listarPendentes(): Observable<SugestaoContencaoJarvis[]> {
    return this.http.get<SugestaoContencaoJarvis[]>(`${this.baseUrl}/pendentes`);
  }

  aceitar(id: number): Observable<SugestaoContencaoJarvis> {
    return this.http.post<SugestaoContencaoJarvis>(`${this.baseUrl}/${id}/aceitar`, {});
  }

  recusar(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/recusar`, {});
  }
}
