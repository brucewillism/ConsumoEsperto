import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Transacao } from '../models/transacao.model';
import { AuthService } from './auth.service';

/**
 * Serviço responsável por gerenciar operações relacionadas a transações financeiras
 * 
 * Este serviço implementa toda a comunicação com a API backend para:
 * - CRUD completo de transações (criar, buscar, atualizar, deletar)
 * - Consultas específicas por período, categoria e usuário
 * - Gerenciamento de headers de autenticação
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Injectable({
  providedIn: 'root' // Singleton disponível em toda a aplicação
})
export class TransacaoService {
  
  // URL base da API de transações no backend
  private readonly API_URL = 'http://localhost:8080/api/transacoes';

  /**
   * Construtor do serviço
   * 
   * @param http Cliente HTTP para comunicação com o backend
   * @param authService Serviço de autenticação para obter tokens JWT
   */
  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  /**
   * Cria headers HTTP com token de autenticação
   * 
   * Método privado que gera headers necessários para todas as requisições,
   * incluindo o token JWT para autenticação e o tipo de conteúdo.
   * 
   * @returns HttpHeaders configurados com autenticação
   */
  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`, // Token JWT para autenticação
      'Content-Type': 'application/json' // Tipo de conteúdo JSON
    });
  }

  /**
   * Cria uma nova transação financeira
   * 
   * Envia uma requisição POST para o backend criando uma nova
   * transação (receita ou despesa) no sistema.
   * 
   * @param transacao Dados da transação a ser criada
   * @returns Observable com a transação criada
   */
  criarTransacao(transacao: Transacao): Observable<Transacao> {
    return this.http.post<Transacao>(this.API_URL, transacao, { headers: this.getHeaders() });
  }

  /**
   * Busca uma transação específica por ID
   * 
   * Recupera os detalhes de uma transação específica
   * através de uma requisição GET para o backend.
   * 
   * @param id ID único da transação a ser buscada
   * @returns Observable com a transação encontrada
   */
  buscarPorId(id: number): Observable<Transacao> {
    return this.http.get<Transacao>(`${this.API_URL}/${id}`, { headers: this.getHeaders() });
  }

  /**
   * Lista todas as transações do usuário autenticado
   * 
   * Recupera todas as transações (receitas e despesas) do usuário
   * logado através de uma requisição GET.
   * 
   * @returns Observable com lista de todas as transações do usuário
   */
  buscarPorUsuario(): Observable<Transacao[]> {
    return this.http.get<Transacao[]>(this.API_URL, { headers: this.getHeaders() });
  }

  /**
   * Atualiza uma transação existente
   * 
   * Modifica os dados de uma transação através de uma
   * requisição PUT para o backend.
   * 
   * @param id ID da transação a ser atualizada
   * @param transacao Novos dados da transação
   * @returns Observable com a transação atualizada
   */
  atualizarTransacao(id: number, transacao: Transacao): Observable<Transacao> {
    return this.http.put<Transacao>(`${this.API_URL}/${id}`, transacao, { headers: this.getHeaders() });
  }

  /**
   * Remove uma transação do sistema
   * 
   * Exclui permanentemente uma transação através de uma
   * requisição DELETE para o backend.
   * 
   * @param id ID da transação a ser excluída
   * @returns Observable vazio indicando sucesso
   */
  deletarTransacao(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`, { headers: this.getHeaders() });
  }

  /**
   * Busca transações dentro de um período específico
   * 
   * Filtra transações por intervalo de datas, útil para
   * relatórios mensais, trimestrais ou personalizados.
   * 
   * @param dataInicio Data de início do período
   * @param dataFim Data de fim do período
   * @returns Observable com lista de transações no período
   */
  buscarPorPeriodo(dataInicio: Date, dataFim: Date): Observable<Transacao[]> {
    // Converte as datas para formato ISO string para envio ao backend
    const params = {
      dataInicio: dataInicio.toISOString(),
      dataFim: dataFim.toISOString()
    };
    return this.http.get<Transacao[]>(`${this.API_URL}/periodo`, { 
      headers: this.getHeaders(),
      params: params // Parâmetros de query string
    });
  }

  /**
   * Filtra transações por categoria específica
   * 
   * Útil para análises de gastos por tipo de despesa
   * (alimentação, transporte, lazer, etc.).
   * 
   * @param categoriaId ID da categoria para filtrar
   * @returns Observable com lista de transações da categoria
   */
  buscarPorCategoria(categoriaId: number): Observable<Transacao[]> {
    return this.http.get<Transacao[]>(`${this.API_URL}/categoria/${categoriaId}`, { headers: this.getHeaders() });
  }

  /**
   * Busca transações por período usando strings de data
   * 
   * Versão alternativa do método buscarPorPeriodo que aceita
   * strings de data diretamente. Útil para integração com
   * componentes de calendário.
   * 
   * @param dataInicio String da data de início (formato ISO)
   * @param dataFim String da data de fim (formato ISO)
   * @returns Observable com lista de transações no período
   */
  getTransacoesPorPeriodo(dataInicio: string, dataFim: string): Observable<Transacao[]> {
    return this.http.get<Transacao[]>(`${this.API_URL}/periodo?inicio=${dataInicio}&fim=${dataFim}`, { headers: this.getHeaders() });
  }
}
