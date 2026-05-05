import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

export interface RelatorioGastos {
  categoria: string;
  valor: number;
  percentual: number;
}

export interface RelatorioCategoriaMesAtualItem {
  categoria: string;
  valor: number;
  percentual: number;
}

export interface RelatorioCategoriaMesAtual {
  ano: number;
  mes: number;
  totalDespesas: number;
  itens: RelatorioCategoriaMesAtualItem[];
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
  private readonly API_URL = `${environment.apiUrl}/relatorios`;

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

  private getAuthHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  getRelatorioPorCategoria(ano: number, mes: number): Observable<any> {
    return this.http.get<any>(`${this.API_URL}/categoria?ano=${ano}&mes=${mes}`, { headers: this.getHeaders() });
  }

  getDespesasPorCategoriaMesAtual(): Observable<RelatorioCategoriaMesAtual> {
    return this.http.get<RelatorioCategoriaMesAtual>(`${this.API_URL}/categoria/mes-atual`, { headers: this.getHeaders() });
  }

  getRelatorioMensal(ano: number, mes: number): Observable<any> {
    return this.http.get<any>(`${this.API_URL}/mensal?ano=${ano}&mes=${mes}`, { headers: this.getHeaders() });
  }

  getAlertas(): Observable<any> {
    return this.http.get<any>(`${this.API_URL}/alertas`, { headers: this.getHeaders() });
  }

  getResumoFinanceiro(): Observable<any> {
    return this.http.get(`${environment.apiUrl}/transacoes/resumo`, { headers: this.getHeaders() });
  }

  getRelatorioAnual(ano: number): Observable<any> {
    return this.http.get<any>(`${this.API_URL}/anual?ano=${ano}`, { headers: this.getHeaders() });
  }

  /** PDF: despesas confirmadas do ano (apoio IR); mesmo critério do antigo CSV. */
  exportarIrPdf(ano?: number): Observable<Blob> {
    const params = ano != null ? `?ano=${ano}` : '';
    return this.http.get(`${this.API_URL}/exportar-ir.pdf${params}`, {
      headers: this.getAuthHeaders(),
      responseType: 'blob'
    });
  }

  /** Legado CSV (API); na UI usar exportarIrPdf. */
  exportarIrCsv(ano?: number): Observable<Blob> {
    const params = ano != null ? `?ano=${ano}` : '';
    return this.http.get(`${this.API_URL}/exportar-ir${params}`, {
      headers: this.getAuthHeaders(),
      responseType: 'blob'
    });
  }
}
