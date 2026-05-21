export type TipoContaBancaria = 'CORRENTE' | 'POUPANCA' | 'DINHEIRO';

export interface ContaBancaria {
  id?: number;
  nome: string;
  tipo: TipoContaBancaria;
  saldoAtual: number;
  usuarioId?: number;
  ativa?: boolean;
  padrao?: boolean;
  dataCriacao?: string;
  dataAtualizacao?: string;
}

export const TIPOS_CONTA: { value: TipoContaBancaria; label: string }[] = [
  { value: 'CORRENTE', label: 'Conta corrente' },
  { value: 'POUPANCA', label: 'Poupança' },
  { value: 'DINHEIRO', label: 'Dinheiro / carteira' },
];
