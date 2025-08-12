import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export interface RelatorioGastos {
  categoria: string;
  valor: number;
  percentual: number;
}

export interface RelatorioMensal {
  mes: string;
  receitas: number;
  despesas: number;
  saldo: number;
}

export interface RelatorioCartao {
  cartao: string;
  limite: number;
  utilizado: number;
  disponivel: number;
  percentualUtilizado: number;
}

@Injectable({
  providedIn: 'root'
})
export class RelatorioService {
  private readonly API_URL = 'http://localhost:8080/api/relatorios';

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  getGastosPorCategoria(dataInicio: string, dataFim: string): Observable<RelatorioGastos[]> {
    return this.http.get<RelatorioGastos[]>(`${this.API_URL}/gastos-por-categoria?inicio=${dataInicio}&fim=${dataFim}`, { headers: this.getHeaders() });
  }

  getRelatorioMensal(ano: number): Observable<RelatorioMensal[]> {
    return this.http.get<RelatorioMensal[]>(`${this.API_URL}/mensal/${ano}`, { headers: this.getHeaders() });
  }

  getRelatorioCartoes(): Observable<RelatorioCartao[]> {
    return this.http.get<RelatorioCartao[]>(`${this.API_URL}/cartoes`, { headers: this.getHeaders() });
  }

  getResumoFinanceiro(): Observable<any> {
    return this.http.get(`${this.API_URL}/resumo`, { headers: this.getHeaders() });
  }

  getFluxoCaixa(dataInicio: string, dataFim: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.API_URL}/fluxo-caixa?inicio=${dataInicio}&fim=${dataFim}`, { headers: this.getHeaders() });
  }

  exportarRelatorio(tipo: string, formato: string, dataInicio: string, dataFim: string): Observable<Blob> {
    return this.http.get(`${this.API_URL}/exportar/${tipo}?formato=${formato}&inicio=${dataInicio}&fim=${dataFim}`, { 
      headers: this.getHeaders(),
      responseType: 'blob'
    });
  }
}
