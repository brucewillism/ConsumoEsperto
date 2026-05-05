import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CartaoCredito } from '../models/cartao-credito.model';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CartaoCreditoService {
  private readonly API_URL = `${environment.apiUrl}/cartoes-credito`;

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

  criarCartaoCredito(cartao: CartaoCredito): Observable<CartaoCredito> {
    return this.http.post<CartaoCredito>(this.API_URL, cartao, { headers: this.getHeaders() });
  }

  buscarPorId(id: number): Observable<CartaoCredito> {
    return this.http.get<CartaoCredito>(`${this.API_URL}/${id}`, { headers: this.getHeaders() });
  }

  buscarPorUsuario(): Observable<CartaoCredito[]> {
    return this.http.get<CartaoCredito[]>(this.API_URL, { headers: this.getHeaders() });
  }

  getCartoes(): Observable<CartaoCredito[]> {
    return this.buscarPorUsuario();
  }

  atualizarCartaoCredito(id: number, cartao: CartaoCredito): Observable<CartaoCredito> {
    return this.http.put<CartaoCredito>(`${this.API_URL}/${id}`, cartao, { headers: this.getHeaders() });
  }

  deletarCartaoCredito(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`, { headers: this.getHeaders() });
  }

  getLimiteTotal(): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/limite-total`, { headers: this.getHeaders() });
  }

  getLimiteDisponivel(): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/limite-disponivel`, { headers: this.getHeaders() });
  }

  buscarTodosCartoes(): Observable<CartaoCredito[]> {
    return this.buscarPorUsuario();
  }
}
