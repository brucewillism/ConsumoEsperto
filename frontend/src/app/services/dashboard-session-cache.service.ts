import { Injectable } from '@angular/core';

export interface DashboardCardSnapshot {
  title: string;
  value: string;
  change: string;
  changeType: 'positive' | 'negative' | 'neutral';
  icon: string;
  color: string;
}

export interface RecentTxSnapshot {
  id?: number;
  description: string;
  amount: number;
  category: string;
  date: Date;
  type: 'credit' | 'debit';
  showJurosWarning?: boolean;
}

/** Mantém última vista do dashboard na sessão (evita overlay longo ao voltar à rota). */
export interface DashboardViewSnapshot {
  totalIncome: number;
  totalSpent: number;
  balance: number;
  receitasPrevistasMes: number;
  creditCardLimit: number;
  creditCardUsed: number;
  dashboardCards: DashboardCardSnapshot[];
  recentTransactions: RecentTxSnapshot[];
  savedAt: number;
}

@Injectable({ providedIn: 'root' })
export class DashboardSessionCacheService {
  private snapshot: DashboardViewSnapshot | null = null;

  private readonly maxAgeMs = 5 * 60 * 1000;

  isFresh(): boolean {
    return !!this.snapshot && Date.now() - this.snapshot.savedAt < this.maxAgeMs;
  }

  save(snap: Omit<DashboardViewSnapshot, 'savedAt'>): void {
    this.snapshot = { ...snap, savedAt: Date.now() };
  }

  get(): DashboardViewSnapshot | null {
    return this.isFresh() ? this.snapshot : null;
  }

  clear(): void {
    this.snapshot = null;
  }
}
