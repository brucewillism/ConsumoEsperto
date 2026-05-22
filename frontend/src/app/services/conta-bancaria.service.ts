import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ContaBancaria, ContaBancariaUpdate } from '../models/conta-bancaria.model';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ContaBancariaService {
  private readonly API_URL = `${environment.apiUrl}/contas-bancarias`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    });
  }

  listar(apenasAtivas = true): Observable<ContaBancaria[]> {
    const params = new HttpParams().set('apenasAtivas', String(apenasAtivas));
    return this.http.get<ContaBancaria[]>(this.API_URL, { headers: this.getHeaders(), params });
  }

  /** Alias explícito para seletores de transferência e pagamento de fatura. */
  listarContasAtivas(): Observable<ContaBancaria[]> {
    return this.listar(true);
  }

  buscarPorId(id: number): Observable<ContaBancaria> {
    return this.http.get<ContaBancaria>(`${this.API_URL}/${id}`, { headers: this.getHeaders() });
  }

  patrimonio(): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/patrimonio`, { headers: this.getHeaders() });
  }

  criar(conta: ContaBancaria): Observable<ContaBancaria> {
    return this.http.post<ContaBancaria>(this.API_URL, conta, { headers: this.getHeaders() });
  }

  atualizar(id: number, conta: ContaBancariaUpdate): Observable<ContaBancaria> {
    return this.http.put<ContaBancaria>(`${this.API_URL}/${id}`, conta, { headers: this.getHeaders() });
  }

  inativar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`, { headers: this.getHeaders() });
  }
}
