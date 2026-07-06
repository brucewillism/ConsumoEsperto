import { CreditCardInvoice } from '../../models/credit-card-invoice.model';

/** Grupo de faturas na linha do tempo (um mês/ano). */
export interface FaturaMesGrupo {
  chave: string;
  ano: number;
  mes: number;
  rotuloMes: string;
  faturas: CreditCardInvoice[];
  /** Todas as faturas do grupo estão pagas. */
  somentePagas: boolean;
  /** Mês de referência = mês civil corrente. */
  mesAtual: boolean;
  qtdPagas: number;
  totalPagas: number;
}
