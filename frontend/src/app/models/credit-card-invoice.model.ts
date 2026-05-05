export interface CreditCardInvoice {
  id: string;
  cardId: string;
  bankName: string;
  amount: number;
  dueDate: Date;
  closingDate: Date;
  status: 'PENDING' | 'PAID' | 'OVERDUE' | 'PREVISTA';
  transactions: any[];
}
