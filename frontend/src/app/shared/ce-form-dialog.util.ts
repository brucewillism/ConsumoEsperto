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

/** Estabilização de form fields. Fecho só pelos botões (disableClose: true). */
export function wireCeDialogBehavior<T, R = unknown>(
  ref: MatDialogRef<T, R>,
  _onBackdrop?: () => R | void
): void {
  ref.afterOpened().subscribe(() => stabilizeFormFields());
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

  wireCeDialogBehavior(ref);
  return ref;
}
