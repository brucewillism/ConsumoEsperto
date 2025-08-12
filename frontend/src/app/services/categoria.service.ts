import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Categoria } from '../models/categoria.model';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CategoriaService {
  private readonly API_URL = `${environment.apiUrl}/categorias`;

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

  criarCategoria(categoria: Categoria): Observable<Categoria> {
    return this.http.post<Categoria>(this.API_URL, categoria, { headers: this.getHeaders() });
  }

  buscarPorId(id: number): Observable<Categoria> {
    return this.http.get<Categoria>(`${this.API_URL}/${id}`, { headers: this.getHeaders() });
  }

  buscarPorUsuario(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(this.API_URL, { headers: this.getHeaders() });
  }

  buscarTodas(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(this.API_URL, { headers: this.getHeaders() });
  }

  atualizarCategoria(id: number, categoria: Categoria): Observable<Categoria> {
    return this.http.put<Categoria>(`${this.API_URL}/${id}`, categoria, { headers: this.getHeaders() });
  }

  deletarCategoria(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`, { headers: this.getHeaders() });
  }

  buscarPorNome(nome: string): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(`${this.API_URL}/buscar?nome=${nome}`, { headers: this.getHeaders() });
  }
}
