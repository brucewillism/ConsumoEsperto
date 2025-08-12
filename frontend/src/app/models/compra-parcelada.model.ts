/**
 * Interface que representa uma compra parcelada no sistema ConsumoEsperto
 * 
 * As compras parceladas são transações que se estendem por múltiplos meses,
 * permitindo controle de pagamentos futuros e planejamento financeiro.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export interface CompraParcelada {
  /** ID único da compra no sistema (gerado automaticamente) */
  id?: number;
  
  /** Descrição da compra parcelada (ex: "Notebook", "Móveis") */
  descricao: string;
  
  /** Valor total da compra (soma de todas as parcelas) */
  valorTotal: number;
  
  /** Valor de cada parcela individual */
  valorParcela: number;
  
  /** Número total de parcelas da compra */
  numeroParcelas: number;
  
  /** Número da parcela atual (1, 2, 3, etc.) */
  parcelaAtual: number;
  
  /** Data em que a compra foi realizada */
  dataCompra?: Date;
  
  /** Data de vencimento da primeira parcela */
  dataPrimeiraParcela?: Date;
  
  /** Data de vencimento da última parcela */
  dataUltimaParcela?: Date;
  
  /** Status atual da compra parcelada */
  statusCompra: StatusCompra;
  
  /** ID do cartão de crédito usado na compra (opcional) */
  cartaoCreditoId?: number;
  
  /** ID da categoria da compra (opcional) */
  categoriaId?: number;
  
  /** Data de criação do registro no sistema */
  dataCriacao?: Date;
  
  /** Data da última atualização dos dados da compra */
  dataAtualizacao?: Date;
}

/**
 * Enum que define os possíveis status de uma compra parcelada
 * 
 * Cada status representa uma etapa diferente no ciclo de vida da compra,
 * desde sua criação até a finalização ou cancelamento.
 */
export enum StatusCompra {
  /** Compra ativa com parcelas pendentes */
  ATIVA = 'ATIVA',
  
  /** Compra finalizada com todas as parcelas pagas */
  FINALIZADA = 'FINALIZADA',
  
  /** Compra cancelada (parcelas não serão cobradas) */
  CANCELADA = 'CANCELADA'
}
