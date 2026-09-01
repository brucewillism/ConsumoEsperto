/**
 * Interface que representa uma fatura de cartão de crédito no sistema ConsumoEsperto
 * 
 * As faturas representam o resumo mensal de transações de um cartão,
 * incluindo valores, datas de vencimento e status de pagamento.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export interface Fatura {
  /** ID único da fatura no sistema (gerado automaticamente) */
  id?: number;
  
  /** Valor total da fatura (soma de todas as transações do período) */
  valorFatura: number;
  valorTotal?: number;
  
  /** Valor já pago da fatura (pode ser parcial ou total) */
  valorPago: number;
  
  /** Data de vencimento para pagamento da fatura */
  dataVencimento?: Date;
  
  /** Data de fechamento da fatura (último dia do período) */
  dataFechamento?: Date;
  
  /** Data em que a fatura foi paga (opcional) */
  dataPagamento?: Date;
  
  /** Status atual da fatura (aberta, fechada, paga, etc.) */
  statusFatura: StatusFatura;
  
  /** Número da fatura fornecido pelo banco (opcional) */
  numeroFatura?: string;
  
  /** ID do cartão de crédito associado à fatura */
  cartaoCreditoId?: number;
  
  /** Data de criação do registro no sistema */
  dataCriacao?: Date;
  
  /** Data da última atualização dos dados da fatura */
  dataAtualizacao?: Date;
  
  // Propriedades para compatibilidade com componentes existentes
  /** Valor da fatura (alias para compatibilidade) */
  valor?: number;
  
  /** Mês e ano da fatura em formato string (opcional) */
  mesAno?: string;
  
  /** Status da fatura (alias para compatibilidade) */
  status?: StatusFatura;
  
  // Propriedades para compatibilidade com CreditCardInvoice
  /** ID do cartão (alias para compatibilidade) */
  cardId?: string;
  
  /** Nome do banco emissor (alias para compatibilidade) */
  bankName?: string;
  nomeCartao?: string;
  banco?: string;
  
  /** Valor da fatura (alias para compatibilidade) */
  amount?: number;
  
  /** Data de vencimento (alias para compatibilidade) */
  dueDate?: Date;
  
  /** Data de fechamento (alias para compatibilidade) */
  closingDate?: Date;
  
  /** Lista de transações da fatura (opcional) */
  transactions?: any[];
}

/**
 * Enum que define os possíveis status de uma fatura.
 *
 * CONTRATO: os valores devem ser idênticos ao enum do backend
 * (`Fatura.StatusFatura` em backend/src/main/java/com/consumoesperto/model/Fatura.java).
 * Não adicionar valores aqui sem implementar o estado correspondente no backend —
 * ver teste de contrato `fatura-status-contrato.spec.mjs`.
 * O "fechamento" da fatura é representado pela data de fechamento (closingDate),
 * não por um status próprio.
 */
export enum StatusFatura {
  /** Fatura em aberto (ciclo corrente) */
  ABERTA = 'ABERTA',

  /** Fatura vencida sem pagamento */
  VENCIDA = 'VENCIDA',

  /** Fatura paga integralmente */
  PAGA = 'PAGA',

  /** Fatura com pagamento parcial */
  PARCIAL = 'PARCIAL',

  /** Ciclo futuro (projeção de parcelas) */
  PREVISTA = 'PREVISTA',

  /** Fatura cancelada */
  CANCELADA = 'CANCELADA'
}

/**
 * Interface DTO para comunicação com o backend
 * 
 * Esta interface é usada especificamente para transferência de dados
 * entre o frontend e o backend, mantendo a estrutura simplificada.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export interface FaturaDTO {
  /** ID único da fatura no sistema (gerado automaticamente) */
  id?: number;
  
  /** Valor total da fatura (soma de todas as transações do período) */
  valorFatura: number;
  valorTotal?: number;
  
  /** Valor já pago da fatura (pode ser parcial ou total) */
  valorPago: number;
  
  /** Data de vencimento para pagamento da fatura */
  dataVencimento?: Date;
  
  /** Data de fechamento da fatura (último dia do período) */
  dataFechamento?: Date;
  
  /** Data em que a fatura foi paga (opcional) */
  dataPagamento?: Date;
  
  /** Status atual da fatura (aberta, fechada, paga, etc.) */
  statusFatura: StatusFatura;

  /** Indicador explícito de quitação no backend */
  paga?: boolean;
  
  /** Número da fatura fornecido pelo banco (opcional) */
  numeroFatura?: string;
  
  /** ID do cartão de crédito associado à fatura */
  cartaoCreditoId?: number;
  nomeCartao?: string;
  banco?: string;
  transacoes?: any[];
  
  /** Data de criação do registro no sistema */
  dataCriacao?: Date;
  
  /** Data da última atualização dos dados da fatura */
  dataAtualizacao?: Date;
}
