import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ConfirmDialogComponent, ConfirmDialogData } from '../shared/confirm-dialog.component';

/**
 * Confirmações no estilo J.A.R.V.I.S. — substitui {@code window.confirm} e centraliza o modal.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  constructor(private dialog: MatDialog) {}

  /** Abre o diálogo e emite {@code true} se o utilizador confirmou. */
  ask(data: ConfirmDialogData): Observable<boolean> {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '440px',
      maxWidth: '96vw',
      panelClass: 'jarvis-confirm-dialog',
      autoFocus: 'first-toggler',
      data: this.normalizar(data),
    });
    return ref.afterClosed().pipe(map((v) => !!v));
  }

  private normalizar(data: ConfirmDialogData): ConfirmDialogData {
    const legado = data as ConfirmDialogData & { confirmText?: string };
    return {
      ...data,
      confirmLabel: data.confirmLabel ?? legado.confirmText ?? 'Confirmar',
    };
  }
}
