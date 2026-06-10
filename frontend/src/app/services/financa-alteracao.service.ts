import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime, share } from 'rxjs/operators';
import { DashboardSessionCacheService } from './dashboard-session-cache.service';

/** Origem da mutação — evita recarga duplicada na própria tela. */
export type FinancaAlteracaoOrigem =
  | 'faturas'
  | 'pagamento-fatura'
  | 'transacoes'
  | 'contas'
  | 'transferencia'
  | 'cartoes'
  | 'metas'
  | 'dashboard'
  | 'orcamentos'
  | 'renda';

export interface FinancaAlteracaoEvento {
  origem?: FinancaAlteracaoOrigem;
}

/**
 * Sinaliza alterações financeiras (transações, faturas, contas, metas…)
 * para todas as telas recarregarem dados em segundo plano.
 */
@Injectable({ providedIn: 'root' })
export class FinancaAlteracaoService {
  private readonly alteracoes = new Subject<FinancaAlteracaoEvento>();

  readonly alteracoes$ = this.alteracoes.pipe(debounceTime(80), share());

  constructor(private dashboardSessionCache: DashboardSessionCacheService) {}

  notificar(origem?: FinancaAlteracaoOrigem): void {
    this.dashboardSessionCache.clear();
    this.alteracoes.next({ origem });
  }
}
