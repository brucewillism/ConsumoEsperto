import { CreditCardInvoice } from '../../models/credit-card-invoice.model';

/** Grupo de faturas na linha do tempo (um mês/ano). */
export interface FaturaMesGrupo {
  chave: string;
  ano: number;
  mes: number;
  rotuloMes: string;
  faturas: CreditCardInvoice[];
}
