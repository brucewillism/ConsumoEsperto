/**
 * Interface que representa um cartão de crédito no sistema ConsumoEsperto
 * 
 * Esta interface define a estrutura completa de dados de um cartão,
 * incluindo informações bancárias, limites e status de uso.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export interface CartaoCredito {
  /** ID único do cartão no sistema (gerado automaticamente) */
  id?: number;
  
  /** Nome do cartão para identificação (ex: "Nubank", "Itaú") */
  nome: string;
  
  /** Nome do banco emissor do cartão */
  banco: string;
  
  /** Número do cartão (mascarado para segurança) */
  numeroCartao: string;
  
  /** Limite total de crédito disponível no cartão */
  limiteCredito: number;
  
  /** Limite de crédito ainda disponível para uso (API: total − utilizado na fatura aberta) */
  limiteDisponivel: number;

  /** Gasto na fatura aberta / utilizado (API); alias legado em componentes */
  limiteUtilizado?: number;
  
  /** Data de vencimento do cartão (opcional) */
  dataVencimento?: Date;
  
  /** Tipo do cartão (crédito, débito ou ambos) */
  tipoCartao: TipoCartao;
  
  /** Indica se o cartão está ativo (true) ou bloqueado (false) */
  ativo: boolean;
  
  /** ID do usuário proprietário do cartão */
  usuarioId?: number;
  
  /** Data de criação do registro no sistema */
  dataCriacao?: Date;
  
  /** Data da última atualização dos dados do cartão */
  dataAtualizacao?: Date;
  
  // Propriedades adicionais necessárias pelos componentes
  /** Limite total do cartão (alias para compatibilidade) */
  limite?: number;
  
  /** Bandeira do cartão (Visa, Mastercard, etc.) */
  bandeira?: string;
}

/**
 * Enum que define os tipos possíveis de cartão
 * 
 * CREDITO: apenas funcionalidade de crédito
 * CREDITO_DEBITO: funcionalidade de crédito e débito
 * DEBITO: apenas funcionalidade de débito
 */
export enum TipoCartao {
  /** Cartão apenas de crédito */
  CREDITO = 'CREDITO',
  
  /** Cartão com funcionalidade de crédito e débito */
  CREDITO_DEBITO = 'CREDITO_DEBITO',
  
  /** Cartão apenas de débito */
  DEBITO = 'DEBITO'
}
