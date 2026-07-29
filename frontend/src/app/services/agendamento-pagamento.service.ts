import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type StatusAgendamento = 'AGENDADO' | 'PAUSADO' | 'PAGO' | 'FALHOU' | 'CANCELADO';

export type RecorrenciaAgendamento =
  | 'UNICA'
  | 'DIARIA'
  | 'SEMANAL'
  | 'QUINZENAL'
  | 'MENSAL'
  | 'BIMESTRAL'
  | 'TRIMESTRAL'
  | 'SEMESTRAL'
  | 'ANUAL';

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
  recorrencia?: RecorrenciaAgendamento;
  dataFim?: string;
  proximaExecucao?: string;
  ultimaExecucao?: string;
  diaVencimentoMensal?: number;
  categoriaId?: number;
  categoriaNome?: string;
  cartaoCreditoId?: number;
  cartaoCreditoNome?: string;
  falhasConsecutivas?: number;
}

export interface AgendamentoPagamentoRequest {
  contaDebitoId: number;
  beneficiario: string;
  valor: number;
  dataVencimento: string;
  codigoBarrasOuPix?: string;
  categoriaId?: number;
  cartaoCreditoId?: number;
  recorrencia?: RecorrenciaAgendamento;
  dataFim?: string;
  diaVencimentoMensal?: number;
}

@Injectable({ providedIn: 'root' })
export class AgendamentoPagamentoService {
  private readonly base = `${environment.apiUrl}/agendamentos-pagamentos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<AgendamentoPagamento[]> {
    return this.http.get<AgendamentoPagamento[]>(this.base);
  }

  buscar(id: number): Observable<AgendamentoPagamento> {
    return this.http.get<AgendamentoPagamento>(`${this.base}/${id}`);
  }

  criar(payload: AgendamentoPagamentoRequest): Observable<AgendamentoPagamento> {
    return this.http.post<AgendamentoPagamento>(this.base, payload);
  }

  atualizar(id: number, payload: Partial<AgendamentoPagamentoRequest>): Observable<AgendamentoPagamento> {
    return this.http.put<AgendamentoPagamento>(`${this.base}/${id}`, payload);
  }

  cancelar(id: number): Observable<AgendamentoPagamento> {
    return this.http.delete<AgendamentoPagamento>(`${this.base}/${id}`);
  }

  pausar(id: number): Observable<AgendamentoPagamento> {
    return this.http.post<AgendamentoPagamento>(`${this.base}/${id}/pausar`, {});
  }

  ativar(id: number): Observable<AgendamentoPagamento> {
    return this.http.post<AgendamentoPagamento>(`${this.base}/${id}/ativar`, {});
  }

  marcarPago(id: number): Observable<AgendamentoPagamento> {
    return this.http.post<AgendamentoPagamento>(`${this.base}/${id}/marcar-pago`, {});
  }

  executar(id: number): Observable<AgendamentoPagamento> {
    return this.http.post<AgendamentoPagamento>(`${this.base}/${id}/executar`, {});
  }

  historico(): Observable<AgendamentoPagamento[]> {
    return this.http.get<AgendamentoPagamento[]>(`${this.base}/historico`);
  }
}
