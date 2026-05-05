import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

/**
 * Sinaliza alterações em transações / metas / cartões para o dashboard recarregar o saldo e cards.
 */
@Injectable({ providedIn: 'root' })
export class FinancaAlteracaoService {
  private readonly alteracoes = new Subject<void>();

  readonly alteracoes$ = this.alteracoes.asObservable();

  notificar(): void {
    this.alteracoes.next();
  }
}
