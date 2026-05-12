import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DespesaFixa {
  id?: number;
  descricao: string;
  valor: number;
  diaVencimento: number;
  categoria?: string | null;
}

@Injectable({ providedIn: 'root' })
export class DespesasFixaService {
  private readonly base = `${environment.apiUrl}/despesas-fixas`;

  constructor(private http: HttpClient) {}

  listar(): Observable<DespesaFixa[]> {
    return this.http.get<DespesaFixa[]>(this.base);
  }

  criar(body: DespesaFixa): Observable<DespesaFixa> {
    return this.http.post<DespesaFixa>(this.base, body);
  }

  atualizar(id: number, body: DespesaFixa): Observable<DespesaFixa> {
    return this.http.put<DespesaFixa>(`${this.base}/${id}`, body);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
