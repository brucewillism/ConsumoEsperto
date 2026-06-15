import { ComponentType } from '@angular/cdk/overlay';
import { TemplateRef } from '@angular/core';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';

function mergePanelClass(config?: MatDialogConfig): string | string[] {
  const extra = config?.panelClass;
  const extras = extra ? (Array.isArray(extra) ? extra : [extra]) : [];
  return ['ce-form-dialog', ...extras];
}

/** Recalcula notch/altura dos mat-form-field após o dialog terminar de abrir. */
function stabilizeFormFields(): void {
  requestAnimationFrame(() => {
    window.dispatchEvent(new Event('resize'));
    requestAnimationFrame(() => window.dispatchEvent(new Event('resize')));
  });
}

/**
 * Com {@code disableClose: true}, cliques em mat-select dentro do modal não disparam
 * fechamento acidental. O backdrop continua a fechar ao clicar fora.
 */
export function wireCeDialogBackdropClose<T, R = unknown>(
  ref: MatDialogRef<T, R>,
  onBackdrop?: () => R | void
): void {
  ref.backdropClick().subscribe(() => {
    if (onBackdrop) {
      const result = onBackdrop();
      ref.close(result as R);
    } else {
      ref.close();
    }
  });
}

export function openCeFormDialog<T, D = unknown, R = unknown>(
  dialog: MatDialog,
  componentOrTemplate: ComponentType<T> | TemplateRef<T>,
  config?: MatDialogConfig<D>
): MatDialogRef<T, R> {
  const ref = dialog.open<T, D, R>(componentOrTemplate, {
    maxWidth: '96vw',
    disableClose: true,
    ...config,
    panelClass: mergePanelClass(config),
  });

  wireCeDialogBackdropClose(ref);
  ref.afterOpened().subscribe(() => stabilizeFormFields());
  return ref;
}
