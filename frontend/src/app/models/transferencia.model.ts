export interface TransferenciaRequest {
  contaOrigemId: number;
  contaDestinoId: number;
  valor: number;
  descricao?: string;
  dataTransferencia?: string;
}

export interface TransferenciaConta {
  id: number;
  contaOrigemId: number;
  contaOrigemNome: string;
  contaDestinoId: number;
  contaDestinoNome: string;
  valor: number;
  descricao?: string;
  dataTransferencia: string;
  patrimonioLiquidoApos?: number;
}
