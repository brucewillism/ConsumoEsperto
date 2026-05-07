export interface CreditCardInvoice {
  id: string;
  cardId: string;
  /** Número/código da fatura no banco (evita PUT com body incompleto). */
  numeroFatura?: string;
  bankName: string;
  amount: number;
  dueDate: Date;
  closingDate: Date;
  status: 'PENDING' | 'PAID' | 'OVERDUE' | 'PREVISTA';
  transactions: any[];
}
