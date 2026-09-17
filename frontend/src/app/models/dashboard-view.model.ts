export type DashboardViewMode = 'MONTHLY' | 'GENERAL';

export interface DashboardViewCardItem {
  id?: string;
  titulo: string;
  valor: number;
  subtitulo?: string;
  sentido?: 'positivo' | 'negativo' | 'neutro' | string;
}

export interface DashboardViewLine {
  codigo: string;
  titulo: string;
  detalhe: string;
}

export interface DashboardViewPayload {
  viewMode: DashboardViewMode | string;
  periodo: string;
  cards?: {
    itens?: DashboardViewCardItem[];
  };
  metricas?: Record<string, unknown>;
  compromissos?: Record<string, unknown>;
  insights?: DashboardViewLine[];
  alertas?: DashboardViewLine[];
}

export const DASHBOARD_VIEW_STORAGE_KEY = 'ce.dashboard.viewMode';

export function lerVisaoDashboard(storage?: Storage | null): DashboardViewMode {
  try {
    const raw = (storage ?? globalThis.localStorage)?.getItem(DASHBOARD_VIEW_STORAGE_KEY);
    return raw === 'GENERAL' ? 'GENERAL' : 'MONTHLY';
  } catch {
    return 'MONTHLY';
  }
}

export function gravarVisaoDashboard(mode: DashboardViewMode, storage?: Storage | null): void {
  try {
    (storage ?? globalThis.localStorage)?.setItem(DASHBOARD_VIEW_STORAGE_KEY, mode);
  } catch {
    /* ignore quota / private mode */
  }
}
