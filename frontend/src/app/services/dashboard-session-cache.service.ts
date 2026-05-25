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

/** Mantém última vista do dashboard (memória + sessionStorage). */
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

const STORAGE_KEY = 'ce.dashboard.snapshot.v1';

@Injectable({ providedIn: 'root' })
export class DashboardSessionCacheService {
  private snapshot: DashboardViewSnapshot | null = null;

  private readonly maxAgeMs = 5 * 60 * 1000;

  constructor() {
    this.hydrateFromStorage();
  }

  isFresh(): boolean {
    this.hydrateFromStorage();
    return !!this.snapshot && Date.now() - this.snapshot.savedAt < this.maxAgeMs;
  }

  save(snap: Omit<DashboardViewSnapshot, 'savedAt'>): void {
    this.snapshot = { ...snap, savedAt: Date.now() };
    this.persistToStorage();
  }

  get(): DashboardViewSnapshot | null {
    this.hydrateFromStorage();
    return this.isFresh() ? this.snapshot : null;
  }

  clear(): void {
    this.snapshot = null;
    if (typeof sessionStorage !== 'undefined') {
      try {
        sessionStorage.removeItem(STORAGE_KEY);
      } catch {
        /* ignore quota / private mode */
      }
    }
  }

  private hydrateFromStorage(): void {
    if (this.snapshot != null && Date.now() - this.snapshot.savedAt < this.maxAgeMs) {
      return;
    }
    if (typeof sessionStorage === 'undefined') {
      return;
    }
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw) as DashboardViewSnapshot;
      if (parsed?.savedAt && Date.now() - parsed.savedAt < this.maxAgeMs) {
        this.snapshot = {
          ...parsed,
          recentTransactions: (parsed.recentTransactions ?? []).map((t) => ({
            ...t,
            date: new Date(t.date),
          })),
        };
      }
    } catch {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }

  private persistToStorage(): void {
    if (!this.snapshot || typeof sessionStorage === 'undefined') {
      return;
    }
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(this.snapshot));
    } catch {
      /* ignore */
    }
  }
}
