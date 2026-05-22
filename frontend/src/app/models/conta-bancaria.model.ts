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

/** Payload de edição — saldo não é enviado (atualizado por transações). */
export interface ContaBancariaUpdate {
  nome: string;
  tipo: TipoContaBancaria;
  ativa?: boolean;
  padrao?: boolean;
}

export const TIPOS_CONTA: { value: TipoContaBancaria; label: string }[] = [
  { value: 'CORRENTE', label: 'Conta corrente' },
  { value: 'POUPANCA', label: 'Poupança' },
  { value: 'DINHEIRO', label: 'Dinheiro / carteira' },
];
