import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Fatura, FaturaDTO, StatusFatura } from '../models/fatura.model';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';
import { CreditCardInvoice } from '../models/credit-card-invoice.model';

@Injectable({
  providedIn: 'root'
})
export class FaturaService {
  private readonly API_URL = `${environment.apiUrl}/faturas`;

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

  // Métodos para integração com backend
  criarFatura(fatura: Fatura): Observable<Fatura> {
    const faturaDTO: FaturaDTO = this.converterParaDTO(fatura);
    return this.http.post<FaturaDTO>(this.API_URL, faturaDTO, { headers: this.getHeaders() })
      .pipe(map(dto => this.converterParaModelo(dto)));
  }

  buscarPorId(id: number): Observable<Fatura> {
    return this.http.get<FaturaDTO>(`${this.API_URL}/${id}`, { headers: this.getHeaders() })
      .pipe(map(dto => this.converterParaModelo(dto)));
  }

  buscarPorUsuario(): Observable<Fatura[]> {
    return this.http.get<FaturaDTO[]>(this.API_URL, { headers: this.getHeaders() })
      .pipe(map(dtos => dtos.map(dto => this.converterParaModelo(dto))));
  }

  buscarPorCartao(cartaoId: number): Observable<Fatura[]> {
    return this.http.get<FaturaDTO[]>(`${this.API_URL}/cartao/${cartaoId}`, { headers: this.getHeaders() })
      .pipe(map(dtos => dtos.map(dto => this.converterParaModelo(dto))));
  }

  atualizarFatura(id: number, fatura: Fatura): Observable<Fatura> {
    const faturaDTO: FaturaDTO = this.converterParaDTO(fatura);
    return this.http.put<FaturaDTO>(`${this.API_URL}/${id}`, faturaDTO, { headers: this.getHeaders() })
      .pipe(map(dto => this.converterParaModelo(dto)));
  }

  deletarFatura(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`, { headers: this.getHeaders() });
  }

  getFaturasPorStatus(status: string): Observable<Fatura[]> {
    return this.http.get<FaturaDTO[]>(`${this.API_URL}/status/${status}`, { headers: this.getHeaders() })
      .pipe(map(dtos => dtos.map(dto => this.converterParaModelo(dto))));
  }

  getFaturasPorPeriodo(dataInicio: string, dataFim: string): Observable<Fatura[]> {
    return this.http.get<FaturaDTO[]>(`${this.API_URL}/periodo?dataInicio=${dataInicio}&dataFim=${dataFim}`, { headers: this.getHeaders() })
      .pipe(map(dtos => dtos.map(dto => this.converterParaModelo(dto))));
  }

  getFaturas(): Observable<Fatura[]> {
    return this.buscarPorUsuario();
  }

  excluirFatura(id: number): Observable<void> {
    return this.deletarFatura(id);
  }

  // Métodos para compatibilidade com CreditCardInvoice
  getFaturasCartao(): Observable<CreditCardInvoice[]> {
    return this.buscarPorUsuario().pipe(
      map(faturas => faturas.map(fatura => this.converterParaCreditCardInvoice(fatura)))
    );
  }

  criarFaturaCartao(fatura: CreditCardInvoice): Observable<CreditCardInvoice> {
    const faturaModelo = this.converterParaFatura(fatura);
    return this.criarFatura(faturaModelo).pipe(
      map(fatura => this.converterParaCreditCardInvoice(fatura))
    );
  }

  atualizarFaturaCartao(fatura: CreditCardInvoice): Observable<CreditCardInvoice> {
    const faturaModelo = this.converterParaFatura(fatura);
    if (!fatura.id) {
      throw new Error('ID da fatura é obrigatório para atualização');
    }
    return this.atualizarFatura(Number(fatura.id), faturaModelo).pipe(
      map(fatura => this.converterParaCreditCardInvoice(fatura))
    );
  }

  excluirFaturaCartao(fatura: CreditCardInvoice): Observable<void> {
    if (!fatura.id) {
      throw new Error('ID da fatura é obrigatório para exclusão');
    }
    return this.deletarFatura(Number(fatura.id));
  }

  // Métodos auxiliares de conversão
  private converterParaDTO(fatura: Fatura): FaturaDTO {
    return {
      id: fatura.id,
      valorFatura: fatura.valorFatura || fatura.valor || 0,
      valorPago: fatura.valorPago || 0,
      dataVencimento: fatura.dataVencimento || fatura.dueDate,
      dataFechamento: fatura.dataFechamento || fatura.closingDate,
      dataPagamento: fatura.dataPagamento,
      statusFatura: fatura.statusFatura || this.converterStatus(fatura.status),
      numeroFatura: fatura.numeroFatura,
      cartaoCreditoId: fatura.cartaoCreditoId,
      nomeCartao: fatura.bankName,
      banco: fatura.bankName,
      dataCriacao: fatura.dataCriacao,
      dataAtualizacao: fatura.dataAtualizacao
    };
  }

  private converterParaModelo(dto: FaturaDTO): Fatura {
    return {
      id: dto.id,
      valorFatura: dto.valorFatura,
      valorPago: dto.valorPago,
      dataVencimento: dto.dataVencimento,
      dataFechamento: dto.dataFechamento,
      dataPagamento: dto.dataPagamento,
      statusFatura: dto.statusFatura,
      numeroFatura: dto.numeroFatura,
      cartaoCreditoId: dto.cartaoCreditoId,
      dataCriacao: dto.dataCriacao,
      dataAtualizacao: dto.dataAtualizacao,
      bankName: dto.banco || dto.nomeCartao || 'Banco',
      
      // Propriedades de compatibilidade
      valor: dto.valorFatura,
      status: dto.statusFatura,
      dueDate: dto.dataVencimento,
      closingDate: dto.dataFechamento,
      amount: dto.valorFatura
    };
  }

  private converterParaCreditCardInvoice(fatura: Fatura): CreditCardInvoice {
    return {
      id: fatura.id?.toString() || '',
      cardId: fatura.cartaoCreditoId?.toString() || '',
      bankName: fatura.bankName || fatura.nomeCartao || fatura.banco || 'Banco',
      amount: fatura.valorFatura || fatura.valor || 0,
      dueDate: fatura.dataVencimento || fatura.dueDate || new Date(),
      closingDate: fatura.dataFechamento || fatura.closingDate || new Date(),
      status: this.converterStatusParaCreditCard(fatura.statusFatura || fatura.status),
      transactions: fatura.transactions || []
    };
  }

  private converterParaFatura(fatura: CreditCardInvoice): Fatura {
    return {
      valorFatura: fatura.amount,
      valorPago: 0,
      dataVencimento: fatura.dueDate,
      dataFechamento: fatura.closingDate,
      statusFatura: this.converterStatusDeCreditCard(fatura.status),
      cartaoCreditoId: fatura.cardId ? Number(fatura.cardId) : undefined,
      bankName: fatura.bankName,
      amount: fatura.amount,
      dueDate: fatura.dueDate,
      closingDate: fatura.closingDate,
      transactions: fatura.transactions
    };
  }

  private converterStatus(status: any): StatusFatura {
    if (typeof status === 'string') {
      switch (status) {
        case 'PENDING': return StatusFatura.ABERTA;
        case 'PAID': return StatusFatura.PAGA;
        case 'OVERDUE': return StatusFatura.VENCIDA;
        case 'PREVISTA': return StatusFatura.PREVISTA;
        default: return StatusFatura.ABERTA;
      }
    }
    return StatusFatura.ABERTA;
  }

  private converterStatusParaCreditCard(status: StatusFatura): 'PENDING' | 'PAID' | 'OVERDUE' | 'PREVISTA' {
    switch (status) {
      case StatusFatura.PAGA: return 'PAID';
      case StatusFatura.VENCIDA: return 'OVERDUE';
      case StatusFatura.PREVISTA: return 'PREVISTA';
      default: return 'PENDING';
    }
  }

  private converterStatusDeCreditCard(status: 'PENDING' | 'PAID' | 'OVERDUE' | 'PREVISTA'): StatusFatura {
    switch (status) {
      case 'PAID': return StatusFatura.PAGA;
      case 'OVERDUE': return StatusFatura.VENCIDA;
      case 'PREVISTA': return StatusFatura.PREVISTA;
      default: return StatusFatura.ABERTA;
    }
  }

}
