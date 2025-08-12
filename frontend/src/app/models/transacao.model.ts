/**
 * Interface que representa uma transação financeira no sistema
 * 
 * Uma transação pode ser uma receita (entrada de dinheiro) ou
 * uma despesa (saída de dinheiro). Cada transação está associada
 * a uma categoria e pode ser vinculada a um cartão de crédito.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export interface Transacao {
  /** ID único da transação (opcional para criação) */
  id?: number;
  
  /** Descrição da transação (ex: "Compra no supermercado", "Salário") */
  descricao: string;
  
  /** Valor monetário da transação */
  valor: number;
  
  /** Tipo da transação: RECEITA (entrada) ou DESPESA (saída) */
  tipoTransacao: TipoTransacao;
  
  /** ID da categoria à qual a transação pertence (opcional) */
  categoriaId?: number;
  
  /** Nome da categoria para exibição (opcional) */
  categoriaNome?: string;
  
  /** ID do usuário proprietário da transação (opcional) */
  usuarioId?: number;
  
  /** Data e hora em que a transação ocorreu (opcional) */
  dataTransacao?: Date;
  
  /** Data e hora de criação do registro no sistema (opcional) */
  dataCriacao?: Date;
  
  // Propriedades adicionais necessárias pelos componentes
  /** Tipo da transação (alias para compatibilidade) */
  tipo?: TipoTransacao;
  
  /** Data da transação (alias para compatibilidade) */
  data?: Date;
  
  /** Objeto da categoria completo (opcional) */
  categoria?: any;
  
  /** ID do cartão de crédito associado (opcional) */
  cartaoCreditoId?: number;
}

/**
 * Enum que define os tipos possíveis de transação
 * 
 * RECEITA: entrada de dinheiro (salário, vendas, etc.)
 * DESPESA: saída de dinheiro (compras, contas, etc.)
 */
export enum TipoTransacao {
  /** Entrada de dinheiro */
  RECEITA = 'RECEITA',
  
  /** Saída de dinheiro */
  DESPESA = 'DESPESA'
}
