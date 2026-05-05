import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SimulacaoInvestimento {
  valorInicial: number;
  aporteMensal: number;
  taxaRetorno: number;
  periodo: number;
}

export interface SimulacaoFinanciamento {
  valorBem: number;
  entrada: number;
  taxaJuros: number;
  prazo: number;
}

export interface ResultadoSimulacaoInvestimento {
  valorInicial: number;
  totalAportes: number;
  jurosCompostos: number;
  valorFinal: number;
  projecaoAnual: any[];
}

export interface ResultadoSimulacaoFinanciamento {
  valorBem: number;
  entrada: number;
  valorFinanciado: number;
  valorParcela: number;
  totalPagar: number;
  totalJuros: number;
}

@Injectable({
  providedIn: 'root'
})
export class SimulacaoService {
  private apiUrl = `${environment.apiUrl}/simulacoes`;

  constructor(private http: HttpClient) { }

  simularInvestimento(simulacao: SimulacaoInvestimento): Observable<ResultadoSimulacaoInvestimento> {
    return this.http.post<ResultadoSimulacaoInvestimento>(`${this.apiUrl}/investimento`, simulacao);
  }

  simularFinanciamento(simulacao: SimulacaoFinanciamento): Observable<ResultadoSimulacaoFinanciamento> {
    return this.http.post<ResultadoSimulacaoFinanciamento>(`${this.apiUrl}/financiamento`, simulacao);
  }

}
