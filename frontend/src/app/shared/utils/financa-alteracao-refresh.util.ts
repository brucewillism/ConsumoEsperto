import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs/operators';
import {
  FinancaAlteracaoOrigem,
  FinancaAlteracaoService,
} from '../../services/financa-alteracao.service';

/**
 * Recarrega dados da tela quando outra parte do app altera finanças.
 * `ignorarOrigens` evita segunda requisição quando a própria tela já recarregou.
 */
export function escutarAlteracoesFinanceiras(
  financa: FinancaAlteracaoService,
  destroyRef: DestroyRef,
  reload: () => void,
  ignorarOrigens: FinancaAlteracaoOrigem[] = []
): void {
  const ignorar = new Set(ignorarOrigens);
  financa.alteracoes$
    .pipe(
      filter((e) => !e.origem || !ignorar.has(e.origem)),
      takeUntilDestroyed(destroyRef)
    )
    .subscribe(() => reload());
}
