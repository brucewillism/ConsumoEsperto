export interface PagamentoFaturaRequest {
  faturaId: number;
  contaBancariaId: number;
  valor: number;
  dataPagamento?: string;
}

export interface PagamentoFaturaResponse {
  id?: number;
  descricao?: string;
  valor?: number;
  faturaId?: number;
  contaBancariaId?: number;
  contaBancariaNome?: string;
}
