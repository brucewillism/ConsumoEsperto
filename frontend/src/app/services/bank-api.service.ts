import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, BehaviorSubject } from 'rxjs';
import { environment } from '../../environments/environment';

export interface BankData {
  id: number;
  name: string;
  type: string;
  balance: number;
  limit: number;
  available: number;
  lastSync: string;
}

export interface BankConnection {
  id: number;
  bankName: string;
  status: 'connected' | 'disconnected' | 'pending';
  lastSync: string;
  cardsCount: number;
}

export interface CreditCard {
  id: number;
  name: string;
  number: string;
  limit: number;
  available: number;
  bank: string;
  type: string;
  status: string;
}

export interface Invoice {
  id: number;
  number: string;
  amount: number;
  dueDate: string;
  closeDate: string;
  status: string;
  cardName: string;
}

export interface CreditCardInvoice {
  id: string;
  cardId: string;
  bankName: string;
  amount: number;
  dueDate: Date;
  closingDate: Date;
  status: 'PENDING' | 'PAID' | 'OVERDUE';
  transactions: BankTransaction[];
}

export interface BankTransaction {
  id: string;
  description: string;
  amount: number;
  date: Date;
  category: string;
  type: 'CREDIT' | 'DEBIT';
}

@Injectable({
  providedIn: 'root'
})
export class BankApiService {

  private apiUrl = environment.apiUrl;
  private _connectedBanks$ = new BehaviorSubject<BankConnection[]>([]);

  constructor(private http: HttpClient) { }

  /**
   * Observable para bancos conectados
   */
  get connectedBanks$(): Observable<BankConnection[]> {
    return this._connectedBanks$.asObservable();
  }

  /**
   * Obtém bancos conectados do usuário
   */
  getConnectedBanks(): Observable<BankConnection[]> {
    return this.http.get<BankConnection[]>(`${this.apiUrl}/bank/connected`);
  }

  /**
   * Conecta um novo banco
   */
  connectBank(bankType: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/bank/connect`, { bankType });
  }

  /**
   * Desconecta um banco
   */
  disconnectBank(bankId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/bank/disconnect/${bankId}`);
  }

  /**
   * Sincroniza dados de um banco específico
   */
  syncBankData(bankId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/bank/sync/${bankId}`, {});
  }

  /**
   * Sincroniza dados de todos os bancos conectados
   */
  syncAllBanks(): Observable<any> {
    return this.http.post(`${this.apiUrl}/bank/sync/all`, {});
  }

  /**
   * Obtém cartões de crédito de todos os bancos conectados
   */
  getCreditCards(): Observable<CreditCard[]> {
    return this.http.get<CreditCard[]>(`${this.apiUrl}/bank/credit-cards`);
  }

  /**
   * Obtém faturas de todos os cartões
   */
  getInvoices(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${this.apiUrl}/bank/invoices`);
  }

  /**
   * Obtém saldo consolidado de todos os bancos
   */
  getConsolidatedBalance(): Observable<any> {
    return this.http.get(`${this.apiUrl}/bank/balance/consolidated`);
  }

  /**
   * Obtém estatísticas de sincronização
   */
  getSyncStats(): Observable<any> {
    return this.http.get(`${this.apiUrl}/bank/sync/stats`);
  }

  /**
   * Obtém URL de autorização para conectar banco
   */
  getBankAuthUrl(bankType: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/bank/auth/url/${bankType}`);
  }

  /**
   * Processa callback OAuth2 de um banco
   */
  processOAuthCallback(bankType: string, code: string, state: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/bank/oauth/callback`, {
      bankType,
      code,
      state
    });
  }

  /**
   * Verifica status de conexão de um banco
   */
  checkBankConnectionStatus(bankId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/bank/connection/status/${bankId}`);
  }

  /**
   * Obtém histórico de sincronizações
   */
  getSyncHistory(bankId?: number): Observable<any> {
    const url = bankId 
      ? `${this.apiUrl}/bank/sync/history/${bankId}`
      : `${this.apiUrl}/bank/sync/history`;
    return this.http.get(url);
  }

  /**
   * Força renovação de token de um banco
   */
  forceTokenRefresh(bankId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/bank/token/refresh/${bankId}`, {});
  }

  /**
   * Obtém informações detalhadas de um banco
   */
  getBankDetails(bankId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/bank/details/${bankId}`);
  }

  /**
   * Obtém transações de um banco específico
   */
  getBankTransactions(bankId: number, startDate?: string, endDate?: string): Observable<any> {
    let url = `${this.apiUrl}/bank/transactions/${bankId}`;
    if (startDate && endDate) {
      url += `?startDate=${startDate}&endDate=${endDate}`;
    }
    return this.http.get(url);
  }

  /**
   * Obtém relatório de gastos por categoria
   */
  getSpendingByCategory(bankId?: number, period?: string): Observable<any> {
    let url = `${this.apiUrl}/bank/reports/spending-by-category`;
    if (bankId) {
      url += `?bankId=${bankId}`;
    }
    if (period) {
      url += bankId ? `&period=${period}` : `?period=${period}`;
    }
    return this.http.get(url);
  }

  /**
   * Obtém análise de gastos por período
   */
  getSpendingAnalysis(period: string, bankId?: number): Observable<any> {
    let url = `${this.apiUrl}/bank/reports/spending-analysis?period=${period}`;
    if (bankId) {
      url += `&bankId=${bankId}`;
    }
    return this.http.get(url);
  }

  /**
   * Obtém total de limite de crédito
   */
  getTotalCreditLimit(): number {
    // TODO: Implementar cálculo real baseado nos dados dos cartões
    return 0;
  }

  /**
   * Obtém total de limite disponível
   */
  getTotalAvailableCredit(): number {
    // TODO: Implementar cálculo real baseado nos dados dos cartões
    return 0;
  }

  /**
   * Obtém total de saldo das contas
   */
  getTotalBalance(): number {
    // TODO: Implementar cálculo real baseado nos dados das contas
    return 0;
  }

  /**
   * Salva configuração de API bancária
   */
  saveBankConfig(config: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/bank-api/configs`, config);
  }

  /**
   * Atualiza configuração de API bancária existente
   */
  updateBankConfig(configId: number, config: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/bank-api/configs/${configId}`, config);
  }

  /**
   * Obtém configurações bancárias do usuário
   */
  getBankConfigs(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bank-api/configs`);
  }

  /**
   * Testa a conectividade com o backend
   */
  testBackendConnection(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/bank-api/test`);
  }

  /**
   * Testa a conexão de uma configuração bancária
   */
  testBankConnection(configId: number): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/bank-config/my-configs/${configId}/test`, {});
  }

  /**
   * Obtém cartões de crédito reais de um banco
   */
  getRealCreditCards(bankType: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/bank-api/real/credit-cards/${bankType}`);
  }

  /**
   * Obtém dados consolidados de todos os bancos
   */
  getConsolidatedBankData(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/bank-api/real/consolidated`);
  }

  /**
   * Configura credenciais do Mercado Pago
   */
  configureMercadoPago(config: {
    accessToken: string;
    publicKey: string;
    clientId: string;
    clientSecret: string;
    userId?: string;
  }): Observable<any> {
    // Usar o endpoint padrão de configurações bancárias
    return this.http.post(`${this.apiUrl}/bank-api/configs`, config);
  }
}
