import { Injectable, Inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of, BehaviorSubject, forkJoin } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
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
   * Obtém headers de autenticação
   */
  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

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
    return this.http.get<BankConnection[]>(`${this.apiUrl}/bank/connected`, { headers: this.getHeaders() });
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
    return this.http.get<CreditCard[]>(`${this.apiUrl}/bank/credit-cards`, { headers: this.getHeaders() });
  }

  /**
   * Obtém faturas de todos os cartões
   */
  getInvoices(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${this.apiUrl}/bank/invoices`, { headers: this.getHeaders() });
  }

  /**
   * Obtém saldo consolidado de todos os bancos
   */
  getConsolidatedBalance(): Observable<any> {
    return this.http.get(`${this.apiUrl}/bank/balance/consolidated`, { headers: this.getHeaders() });
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
   * Obtém total de limite de crédito de todos os cartões
   */
  getTotalCreditLimit(): Observable<number> {
    return this.getCreditCards().pipe(
      map(cards => cards.reduce((total, card) => total + (card.limit || 0), 0)),
      catchError(() => of(0))
    );
  }

  /**
   * Obtém total de limite disponível de todos os cartões
   */
  getTotalAvailableCredit(): Observable<number> {
    return this.getCreditCards().pipe(
      map(cards => cards.reduce((total, card) => total + (card.available || 0), 0)),
      catchError(() => of(0))
    );
  }

  /**
   * Obtém total de saldo das contas bancárias
   */
  getTotalBalance(): Observable<number> {
    return this.getConsolidatedBalance().pipe(
      map(data => data?.totalBalance || 0),
      catchError(() => of(0))
    );
  }

  /**
   * Obtém estatísticas consolidadas de todos os bancos
   */
  getConsolidatedStats(): Observable<{
    totalCreditLimit: number;
    totalAvailableCredit: number;
    totalBalance: number;
    totalCards: number;
    connectedBanks: number;
  }> {
    return forkJoin({
      creditCards: this.getCreditCards().pipe(catchError(() => of([]))),
      balance: this.getConsolidatedBalance().pipe(catchError(() => of({ totalBalance: 0 }))),
      connectedBanks: this.getConnectedBanks().pipe(catchError(() => of([])))
    }).pipe(
      map(data => {
        const totalCreditLimit = data.creditCards.reduce((total, card) => total + (card.limit || 0), 0);
        const totalAvailableCredit = data.creditCards.reduce((total, card) => total + (card.available || 0), 0);
        
        return {
          totalCreditLimit,
          totalAvailableCredit,
          totalBalance: data.balance?.totalBalance || 0,
          totalCards: data.creditCards.length,
          connectedBanks: data.connectedBanks.length
        };
      }),
      catchError(() => of({
        totalCreditLimit: 0,
        totalAvailableCredit: 0,
        totalBalance: 0,
        totalCards: 0,
        connectedBanks: 0
      }))
    );
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
   * Sincroniza dados reais do Mercado Pago
   */
  syncMercadoPagoData(): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/mercadopago/sync-data`, {});
  }

  /**
   * Testa a API do Mercado Pago com dados completos
   */
  testarMercadoPago(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/teste/mercadopago/dados-brutos`);
  }

  /**
   * Obtém status da sincronização do Mercado Pago
   */
  getMercadoPagoSyncStatus(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/mercadopago/sync-status`);
  }

  /**
   * Obtém status da configuração do Mercado Pago
   */
  getMercadoPagoConfigStatus(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/mercadopago/config/status`);
  }

  /**
   * Configura credenciais do Mercado Pago (versão segura individual)
   */
  configureMercadoPagoCredentials(credentials: {
    clientId: string;
    clientSecret: string;
    userId: string;
  }): Observable<any> {
    return this.http.post(`${this.apiUrl}/mercadopago/configure`, credentials);
  }

  /**
   * Configura credenciais do Mercado Pago (versão legada)
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

  /**
   * Configura automaticamente as credenciais do Mercado Pago
   */
  configurarMercadoPagoAutomatico(): Observable<any> {
    return this.http.post(`${this.apiUrl}/mercadopago/auto-config/configure`, {});
  }

  /**
   * Verifica o status da configuração do Mercado Pago
   */
  verificarStatusMercadoPago(): Observable<any> {
    return this.http.get(`${this.apiUrl}/mercadopago/auto-config/status`);
  }

  /**
   * Sincroniza dados reais do Mercado Pago
   */
  syncMercadoPagoRealData(userId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/real-sync/mercadopago?userId=${userId}`, {});
  }

  /**
   * Limpa categorias mock e sincroniza dados reais
   */
  limparESincronizarDadosReais(userId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/real-sync/limpar-e-sincronizar?userId=${userId}`, {});
  }

  /**
   * Testa conexão com Mercado Pago
   */
  testarConexaoMercadoPago(userId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/real-sync/test-connection?userId=${userId}`);
  }

  /**
   * Verifica e sincroniza automaticamente se necessário
   */
  verificarESincronizar(userId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/real-sync/verificar-e-sincronizar?userId=${userId}`, {});
  }

  // Configuração do Mercado Pago
  configurarMercadoPago(credentials: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/mercadopago/config/credentials`, credentials);
  }

  obterStatusMercadoPago(): Observable<any> {
    return this.http.get(`${this.apiUrl}/mercadopago/config/status`);
  }
}
