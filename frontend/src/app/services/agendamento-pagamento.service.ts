import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type StatusAgendamento = 'AGENDADO' | 'PAGO' | 'FALHOU' | 'CANCELADO';

export interface AgendamentoPagamento {
  id: number;
  contaDebitoId?: number;
  contaDebitoNome?: string;
  beneficiario: string;
  valor: number;
  dataVencimento: string;
  codigoBarrasOuPix?: string;
  status: StatusAgendamento;
  dataCriacao?: string;
  dataProcessamento?: string;
  mensagemErro?: string;
}

@Injectable({ providedIn: 'root' })
export class AgendamentoPagamentoService {
  private readonly base = `${environment.apiUrl}/agendamentos-pagamentos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<AgendamentoPagamento[]> {
    return this.http.get<AgendamentoPagamento[]>(this.base);
  }

  cancelar(id: number): Observable<AgendamentoPagamento> {
    return this.http.delete<AgendamentoPagamento>(`${this.base}/${id}`);
  }
}
