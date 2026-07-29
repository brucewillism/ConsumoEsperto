/** Filtros opcionais combináveis para exportação CSV de transações (espelha o backend). */
export interface ExportacaoTransacaoFiltro {
  dataInicio?: string;
  dataFim?: string;
  contaId?: number;
  cartaoId?: number;
  categoriaId?: number;
  tipoTransacao?: string;
  statusConferencia?: string;
  descricaoContem?: string;
}

export interface ExportacaoCsvDownload {
  blob: Blob;
  nomeArquivo: string;
}
