import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AssinaturaRecorrente {
  id?: number;
  nome: string;
  valor: number;
  diaVencimento: number;
  contaDebitoPadraoId?: number | null;
  contaDebitoPadraoNome?: string | null;
  ativo: boolean;
}

@Injectable({ providedIn: 'root' })
export class AssinaturaRecorrenteService {
  private readonly base = `${environment.apiUrl}/assinaturas`;

  constructor(private http: HttpClient) {}

  listar(): Observable<AssinaturaRecorrente[]> {
    return this.http.get<AssinaturaRecorrente[]>(this.base);
  }

  criar(body: AssinaturaRecorrente): Observable<AssinaturaRecorrente> {
    return this.http.post<AssinaturaRecorrente>(this.base, body);
  }

  atualizar(id: number, body: AssinaturaRecorrente): Observable<AssinaturaRecorrente> {
    return this.http.put<AssinaturaRecorrente>(`${this.base}/${id}`, body);
  }

  alternarAtivo(id: number, ativo: boolean): Observable<AssinaturaRecorrente> {
    return this.http.patch<AssinaturaRecorrente>(`${this.base}/${id}/ativo`, { ativo });
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
