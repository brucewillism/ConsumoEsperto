export interface CreditCardInvoice {
  /** Preenchido após persistência no backend. */
  id?: string;
  cardId: string;
  /** Número/código da fatura no banco (evita PUT com body incompleto). */
  numeroFatura?: string;
  bankName: string;
  amount: number;
  dueDate: Date;
  closingDate: Date;
  status: 'PENDING' | 'PAID' | 'PARTIAL' | 'OVERDUE' | 'PREVISTA';
  valorPago?: number;
  /** Quitação fora do app (importação de fatura já paga no banco). */
  origemQuitacao?: 'APP' | 'EXTERNA' | string;
  transactions: any[];
}
