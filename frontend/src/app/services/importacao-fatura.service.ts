import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ImportacaoFaturaItem {
  data: string;
  descricao: string;
  valor: number;
  parcelaAtual?: number | null;
  totalParcelas?: number | null;
  novo: boolean;
  selecionado: boolean;
}

export interface ImportacaoFatura {
  id: number;
  cartaoCreditoId?: number | null;
  bancoCartao: string;
  dataVencimento?: string | null;
  dataFechamento?: string | null;
  valorTotal: number;
  pagamentoMinimo: number;
  status: string;
  novosDetectados: number;
  itens: ImportacaoFaturaItem[];
  auditorias: string[];
  dataCriacao: string;
}

@Injectable({ providedIn: 'root' })
export class ImportacaoFaturaService {
  private readonly base = `${environment.apiUrl}/importacoes/faturas`;

  constructor(private http: HttpClient) {}

  pendentes(): Observable<ImportacaoFatura[]> {
    return this.http.get<ImportacaoFatura[]>(`${this.base}/pendentes`);
  }

  confirmar(id: number, indices: number[]): Observable<{ criadas: number }> {
    return this.http.post<{ criadas: number }>(`${this.base}/${id}/confirmar`, { indices });
  }

  upload(file: File): Observable<ImportacaoFatura> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportacaoFatura>(`${this.base}/upload`, form);
  }
}
